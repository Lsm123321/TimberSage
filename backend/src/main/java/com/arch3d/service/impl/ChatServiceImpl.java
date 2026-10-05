package com.arch3d.service.impl;

import com.arch3d.client.DeepSeekClient;
import com.arch3d.dto.ChatFeedbackRequest;
import com.arch3d.dto.ChatStreamMeta;
import com.arch3d.entity.AiChatHistory;
import com.arch3d.entity.BuildingModel;
import com.arch3d.entity.User;
import com.arch3d.repository.AiChatHistoryRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final DeepSeekClient deepSeekClient;
    private final BuildingModelRepository modelRepository;
    private final AiChatHistoryRepository chatHistoryRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "chat:rate:limit:";
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<)\"']+");
    private static final int MAX_REFERENCE_COUNT = 6;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void streamChat(String question, Integer modelId, String sessionId, Integer userId,
                          Consumer<String> onChunk, Consumer<ChatStreamMeta> onComplete, Consumer<Exception> onError) throws IOException {

        Instant start = Instant.now();
        // Redis 计数限流，一分钟最多 5 次，防止有人刷大模型接口
        if (!checkRateLimit(userId)) {
            onError.accept(new RuntimeException("请求过于频繁，请稍后再试"));
            return;
        }

        // 带上当前模型的元信息当上下文，回答能贴着模型来，不会泛泛而谈
        BuildingModel model = null;
        String modelName = null;
        String dynasty = null;
        String location = null;

        if (modelId != null) {
            model = modelRepository.findById(modelId).orElse(null);
            if (model != null) {
                modelName = model.getModelName();
                dynasty = model.getDynasty();
                location = model.getLocation();
            }
        }

        log.info("Chat stream start: sessionId={}, userId={}, modelId={}, questionLen={}",
            sessionId, userId, modelId, question != null ? question.length() : 0);

        // 边生成边把 chunk 抛回给 Controller（转成 SSE message 事件），
        // 生成结束后再构造 meta（引用/置信度）一起回传
        StringBuilder answerBuffer = new StringBuilder();
        deepSeekClient.streamChat(question, modelId != null ? modelId.toString() : null, 
                modelName, dynasty, location, new DeepSeekClient.DeepSeekCallback() {
                    @Override
                    public void onChunk(String chunk) {
                        answerBuffer.append(chunk);
                        onChunk.accept(chunk);
                    }

                    @Override
                    public void onComplete() {
                        log.info("Chat stream complete: sessionId={}, costMs={}",
                            sessionId, Duration.between(start, Instant.now()).toMillis());
                        String answerText = answerBuffer.toString();
                        // 回答完了再算一遍引用和置信度，给前端展示用
                        onComplete.accept(buildStreamMeta(answerText));
                    }

                    @Override
                    public void onError(Exception e) {
                        log.warn("Chat stream error: sessionId={}, costMs={}, msg={}",
                            sessionId, Duration.between(start, Instant.now()).toMillis(), e.getMessage());
                        onError.accept(e);
                    }
                });
    }

    @Override
    @Async
    @Transactional
    public void saveChatHistory(String sessionId, Integer userId, Integer modelId, String question, String answer) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

            BuildingModel model = null;
            if (modelId != null) {
                model = modelRepository.findById(modelId).orElse(null);
            }

            AiChatHistory chatHistory = AiChatHistory.builder()
                .sessionId(sessionId)
                .user(user)
                .model(model)
                .userQuestion(question)
                .aiAnswer(answer)
                // 记一下用的哪个模型，以后换模型统计时好区分
                .aiModel("deepseek")
                .tokensUsed(estimateTokensByChars(question + answer))
                .helpful(null)
                .createdAt(LocalDateTime.now())
                .build();

            chatHistoryRepository.save(chatHistory);
            log.info("Chat history saved: sessionId={}, userId={}", sessionId, userId);
        } catch (Exception e) {
            log.error("Failed to save chat history", e);
        }
    }

    @Override
    @Transactional
    public void saveChatFeedback(Integer chatId, ChatFeedbackRequest feedback, Integer userId) {
        AiChatHistory chatHistory = chatHistoryRepository.findById(chatId)
            .orElseThrow(() -> new RuntimeException("聊天记录不存在"));

        if (!chatHistory.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权限评价此聊天记录");
        }

        chatHistory.setHelpful(feedback.getHelpful() == 1);
        chatHistoryRepository.save(chatHistory);
    }

    @Override
    public Page<AiChatHistory> getUserChatHistory(Integer userId, int page, int pageSize) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.max(pageSize, 1);
        // 个人中心只展示模型页来的问答，别把导览类对话混进来
        return chatHistoryRepository.findByUserIdAndModelIsNotNullOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize));
    }

    @Override
    @Transactional
    public AiChatHistory updateChatNote(Integer chatId, Integer userId, String chatNote) {
        AiChatHistory chatHistory = chatHistoryRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new RuntimeException("聊天记录不存在或无权限"));
        chatHistory.setChatNote(chatNote == null ? null : chatNote.trim());
        return chatHistoryRepository.save(chatHistory);
    }

    @Override
    @Transactional
    public AiChatHistory generateChatNote(Integer chatId, Integer userId) {
        AiChatHistory chatHistory = chatHistoryRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new RuntimeException("聊天记录不存在或无权限"));
        String question = chatHistory.getUserQuestion() == null ? "" : chatHistory.getUserQuestion().trim();
        String answer = chatHistory.getAiAnswer() == null ? "" : chatHistory.getAiAnswer().trim();
        String briefAnswer = answer.length() > 160 ? answer.substring(0, 160) + "..." : answer;
        String note = "问题摘要：" + question + "\n"
            + "核心结论：" + briefAnswer + "\n"
            + "后续行动：可在该模型详情页继续验证关键结构点。";
        chatHistory.setChatNote(note);
        return chatHistoryRepository.save(chatHistory);
    }

    @Override
    @Transactional
    public void deleteChatHistory(Integer chatId, Integer userId) {
        AiChatHistory chatHistory = chatHistoryRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new RuntimeException("聊天记录不存在或无权限"));
        chatHistoryRepository.delete(chatHistory);
    }

    @Override
    public boolean checkRateLimit(Integer userId) {
        if (userId == null) {
            return true; // 匿名请求不限制
        }

        String key = RATE_LIMIT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, RATE_LIMIT_WINDOW);
        }

        return count <= MAX_REQUESTS_PER_MINUTE;
    }

    /**
     * 粗略估 token 数（按 4 字符 ≈ 1 token），只用来展示统计，别当真。
     */
    private int estimateTokensByChars(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(text.length() / 4, 1);
    }

    private ChatStreamMeta buildStreamMeta(String answer) {
        List<String> references = extractReferences(answer);
        List<String> evidenceItems = extractEvidenceItems(answer);
        boolean noEvidence = references.isEmpty() && evidenceItems.isEmpty();
        String confidence = evaluateConfidence(answer, references, evidenceItems, noEvidence);

        return ChatStreamMeta.builder()
            .confidence(confidence)
            .references(references)
            .evidenceItems(evidenceItems)
            .noEvidence(noEvidence)
            .generatedAt(LocalDateTime.now().format(DATETIME_FORMATTER))
            .build();
    }

    // 从回答里抽 URL 当引用来源，最多 6 条去重
    private List<String> extractReferences(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(answer);
        while (matcher.find() && unique.size() < MAX_REFERENCE_COUNT) {
            String url = matcher.group();
            if (url != null && !url.isBlank()) {
                unique.add(url.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    // prompt 里约定了"参考依据："段落，这里把它后面的行逐条抽出来
    private List<String> extractEvidenceItems(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        String marker = "参考依据：";
        int idx = answer.lastIndexOf(marker);
        if (idx < 0) {
            return List.of();
        }
        String section = answer.substring(idx + marker.length()).trim();
        if (section.isBlank()) {
            return List.of();
        }

        String[] lines = section.split("\\r?\\n");
        List<String> items = new ArrayList<>();
        for (String raw : lines) {
            String cleaned = raw == null ? "" : raw.replaceFirst("^[-*]\\s*", "").trim();
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
            if (items.size() >= MAX_REFERENCE_COUNT) {
                break;
            }
        }
        return items;
    }

    // 置信度没有精确算法，就按"有没有证据 + 回答里有没有含糊措辞"粗判三档
    private String evaluateConfidence(String answer, List<String> references, List<String> evidenceItems, boolean noEvidence) {
        if (noEvidence) {
            return "low";
        }
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        boolean uncertain = text.contains("不确定") || text.contains("无法确认")
            || text.contains("可能") || text.contains("猜测")
            || text.contains("暂无可靠依据");
        int evidenceScore = references.size() + evidenceItems.size();
        if (evidenceScore >= 3 && !uncertain) {
            return "high";
        }
        return uncertain ? "low" : "medium";
    }
}

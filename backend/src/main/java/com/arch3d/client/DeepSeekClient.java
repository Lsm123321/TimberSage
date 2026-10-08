package com.arch3d.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * DeepSeek 的 API Key。只从环境变量注入，别写死到配置文件里提交上去。
     * 对应配置：spring.deepseek.api-key -> SPRING_DEEPSEEK_API_KEY
     */
    @Value("${spring.deepseek.api-key}")
    private String apiKey;

    /**
     * DeepSeek Chat Completions 接口地址，默认就是官方的 /v1/chat/completions。
     * 走自建网关/代理时改这里就行。
     */
    @Value("${spring.deepseek.base-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    /**
     * 智谱 AI 备用通道（chat/completions 与 DeepSeek 接口规范兼容，OpenAI 风格）。
     * DeepSeek 高峰拥堵（503 或首 token 极慢）时自动降级到智谱，保证 AI 功能可用。
     */
    @Value("${spring.zhipu.api-key:}")
    private String zhipuApiKey;

    @Value("${spring.zhipu.chat-url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String zhipuChatUrl;

    @Value("${spring.zhipu.chat-model:glm-4-flash}")
    private String zhipuChatModel;

    /**
     * 首个 token 的最长等待时间。DeepSeek 共享通道高峰时曾出现等 8 分钟才出首 token 的情况，
     * 而 SSE 连接 5 分钟就超时了，用户只会看到转圈后失败——所以等到这个时间还没吐字就放弃主通道，
     * 中断本次读取并交给上层走智谱备胎。
     */
    @Value("${spring.deepseek.first-token-timeout-seconds:45}")
    private long firstTokenTimeoutSeconds;

    /**
     * 相邻 token 的最大间隔。高峰期 DeepSeek 首 token 正常但后续吐字极慢（实测 1 字/20 秒），
     * 间隔超过这个值同样切智谱备胎。
     */
    @Value("${spring.deepseek.token-gap-timeout-seconds:20}")
    private long tokenGapTimeoutSeconds;

    /**
     * 流式调 DeepSeek（stream=true）。
     * 返回的是 SSE 风格的行（"data: " 开头，最后一条是 "[DONE]"），
     * 把增量 token（choices[0].delta.content）一条条通过 callback.onChunk 抛给上层。
     * 这个方法是阻塞的（execute + 逐字符读），调用方要放到自己的线程里，别占用 Web 请求线程。
     *
     * 主通道（DeepSeek）失败或首 token 超时时，自动切智谱 glm-4-flash 备胎重放同一个问题。
     * 回调有"只允许完整走一轮"的幂等保护：备胎开讲后主线程残留的读循环不会再推内容。
     */
    public void streamChat(String prompt, String modelId, String modelName, String dynasty, String location,
                          DeepSeekCallback callback) {
        Instant start = Instant.now();
        AtomicBoolean delivered = new AtomicBoolean(false);
        try {
            // 主备策略：DeepSeek 共享通道高峰期会 503，或首 token 后慢到 1 字/20 秒（8 分钟吐不完一段回答，
            // SSE 连接 5 分钟就超时）。智谱 glm-4-flash 实测稳定，所以平时主用智谱，
            // 智谱失败再回退 DeepSeek——两个通道都走同样的 OpenAI 兼容协议。
            String zKey = Objects.toString(zhipuApiKey, "").trim();
            Exception primaryError = null;
            if (!zKey.isEmpty()) {
                primaryError = tryStreamChat(zhipuChatUrl, zKey, zhipuChatModel,
                        buildSystemPrompt(modelName, dynasty, location), prompt, callback, delivered);
                if (primaryError == null) {
                    return; // 智谱完整走完
                }
                log.warn("智谱流式调用失败，尝试 DeepSeek 通道: {}", primaryError.getMessage());
            }

            Exception backupError = tryStreamChat(apiUrl, Objects.toString(apiKey, "").trim(), "deepseek-chat",
                    buildSystemPrompt(modelName, dynasty, location), prompt, callback, delivered);
            if (backupError != null) {
                callback.onError(primaryError != null ? primaryError : backupError);
            }
        } catch (Exception e) {
            log.error("Error calling LLM API", e);
            callback.onError(e);
        }
    }

    /**
     * 走一次流式补全。返回 null 表示完整消费到 [DONE]；返回异常表示这次没走完（可换通道重试）。
     * delivered 标记：一旦向 callback 推过内容或完成事件，就认为这一轮已经"交付"，
     * 后续的失败只记日志，不再触发换通道，避免用户收到两份回答。
     */
    private Exception tryStreamChat(String url, String key, String model, String systemPrompt, String prompt,
                                    DeepSeekCallback callback, AtomicBoolean delivered) {
        Instant start = Instant.now();
        long startMs = System.currentTimeMillis();
        // watchdog 的停止开关放 try 外面，finally 才能引用到
        AtomicBoolean finished = new AtomicBoolean(false);
        try {
            if (key.isEmpty()) {
                return new IllegalStateException("API Key 未配置");
            }

            // 按 OpenAI 风格的 chat/completions 规范拼请求体（DeepSeek 与智谱都兼容）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", true);

            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);

            requestBody.put("messages", new Object[]{systemMessage, userMessage});
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2048);

            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

            Instant reqStart = Instant.now();

            // 堵塞读中断器：reader.read() 卡在 SSL 读时，循环里的超时检查不会执行，
            // response.close() 也叫不醒已挂起的 read；只有 call.cancel() 能立刻打断 socket。
            // 所以起一个守护线程盯 lastActivityAt，超时就 cancel 当次调用，read 会立刻抛 IOException。
            // finished 在方法开头声明过，这里直接复用。
            java.util.concurrent.atomic.AtomicLong lastActivityAt = new java.util.concurrent.atomic.AtomicLong(
                    System.currentTimeMillis());
            Call call = okHttpClient.newCall(request);
            Thread watchdog = new Thread(() -> {
                try {
                    while (!finished.get()) {
                        Thread.sleep(1000);
                        // 判定拥堵：从未吐字但总时长超限，或吐过字但最近 token 间隔超限
                        boolean neverTalked = !delivered.get()
                                && (System.currentTimeMillis() - startMs) > firstTokenTimeoutSeconds * 1000;
                        boolean stalled = delivered.get()
                                && (System.currentTimeMillis() - lastActivityAt.get()) > tokenGapTimeoutSeconds * 1000;
                        if (neverTalked || stalled) {
                            log.warn("{} 通道拥堵（neverTalked={} stalled={}），取消连接切备胎", model, neverTalked, stalled);
                            call.cancel(); // 阻塞中的 read 立刻抛 IOException
                            return;
                        }                    }
                } catch (InterruptedException ignored) {
                    // 主流程结束了，watchdog 退出即可
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            Response response;
            try {
                response = call.execute(); // 已被 cancel 时这里抛 IOException，正好走换通道逻辑
            } catch (IOException e) {
                finished.set(true);
                return new IOException(model + " 建立连接失败或被看门狗取消: " + e.getMessage());
            }
            if (!response.isSuccessful()) {
                String errBody = "";
                ResponseBody bodyObj = response.body();
                if (bodyObj != null) {
                    errBody = bodyObj.string();
                }
                finished.set(true);
                return new IOException(model + "请求失败: HTTP " + response.code()
                        + (errBody.isBlank() ? "" : " - " + truncate(errBody)));
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                finished.set(true);
                return new IOException("Response body is null");
            }

            log.info("{} stream started: costMs={}, httpCode={}", model,
                Duration.between(reqStart, Instant.now()).toMillis(),
                response.code());

            // 流式返回一行一个 data: {...}。逐字符读、凑够一行就解析，
            // 这样不用引入完整的 SSE 客户端，够用了
            boolean firstToken = true;
            Instant lastTokenAt = Instant.now();
            try (var reader = responseBody.charStream()) {
                StringBuilder buffer = new StringBuilder();
                int c;
                while ((c = reader.read()) != -1) {
                    char ch = (char) c;
                    if (ch == '\n' && buffer.length() > 0) {
                        String line = buffer.toString().trim();
                        buffer.setLength(0);

                        if (line.startsWith("data: ")) {
                            line = line.substring(6);
                            if (line.equals("[DONE]")) {
                                if (!delivered.get()) {
                                    // 一次内容都没吐就 DONE（如纯心跳响应），仍要通知上层正常收尾
                                    delivered.set(true);
                                }
                                callback.onComplete();
                                return null;
                            }
                            try {
                                // 增量内容在 choices[0].delta.content 里
                                JsonNode jsonNode = objectMapper.readTree(line);
                                JsonNode choicesNode = jsonNode.get("choices");
                                if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                                    JsonNode choiceNode = choicesNode.get(0);
                                    JsonNode deltaNode = choiceNode.get("delta");
                                    if (deltaNode != null && deltaNode.has("content")) {
                                        String content = deltaNode.get("content").asText();
                                        if (content != null && !content.isEmpty()) {
                                            if (firstToken) {
                                                firstToken = false;
                                                log.info("{} first token: totalCostMs={}", model,
                                                    Duration.between(start, Instant.now()).toMillis());
                                            }
                                            lastTokenAt = Instant.now();
                                            lastActivityAt.set(System.currentTimeMillis());
                                            // delivered 只用于"这一轮是否已开始交付"的判定（决定换通道/正常收尾），
                                            // 不能拿它挡每个 token —— 否则只有第一个 token 能到用户手里。
                                            delivered.set(true);
                                            callback.onChunk(content);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error parsing response line: {}", line, e);
                            }
                        }
                    } else if (ch != '\r') {
                        buffer.append(ch);
                    }

                    // 首 token 超时保护与 token 间隔保护主要靠上面的 watchdog 线程执行
                    // （阻塞读时循环体不会转动）；这里保留循环内的轻量检查作为双保险。
                    if (firstToken && !delivered.get()
                            && Duration.between(start, Instant.now()).getSeconds() > firstTokenTimeoutSeconds) {
                        return new IOException(model + " 首 token 等待超过 " + firstTokenTimeoutSeconds
                                + "s，判定通道拥堵");
                    }

                    if (!firstToken && delivered.get()
                            && Duration.between(lastTokenAt, Instant.now()).getSeconds() > tokenGapTimeoutSeconds) {
                        return new IOException(model + " token 间隔超过 " + tokenGapTimeoutSeconds
                                + "s，判定通道拥堵");
                    }
                }
            }
            // 流关闭但没收到 [DONE]：算异常收尾，让上层决定要不要换通道
            if (!delivered.get()) {
                return new IOException(model + " 流式响应提前结束（未收到 DONE）");
            }
            return null;

        } catch (Exception e) {
            if (delivered.get()) {
                // 已经推过部分内容就被打断：备胎重放会让用户收到两份回答的拼接。
                // 但直接掐断体验太差，所以对外当作正常结束（前端已有大半内容），
                // 同时补一个 complete 事件让前端停掉"输入中"状态。
                log.warn("{} 流中途被打断（已交付部分内容），按正常结束处理", model);
                callback.onComplete();
                return null;
            }
            return e;
        } finally {
            finished.set(true); // 通知 watchdog 停止轮询
        }
    }

    // 非流式补全的默认参数版本（构件中文命名等场景用）
    public String completeChat(String systemContent, String userContent) throws IOException {
        return completeChat(systemContent, userContent, 0.3, 4096);
    }

    /**
     * 非流式补全（stream=false），可以自己定 temperature 和 max_tokens。
     * 主用智谱 glm-4-flash（高峰实测稳定），失败自动回退 DeepSeek。
     */
    public String completeChat(String systemContent, String userContent, double temperature, int maxTokens) throws IOException {
        String zKey = Objects.toString(zhipuApiKey, "").trim();
        if (!zKey.isEmpty()) {
            try {
                return doCompleteChat(zhipuChatUrl, zKey, zhipuChatModel,
                        systemContent, userContent, temperature, maxTokens);
            } catch (Exception primary) {
                log.warn("智谱非流式调用失败，尝试 DeepSeek 通道: {}", primary.getMessage());
            }
        }
        return doCompleteChat(apiUrl, Objects.toString(apiKey, "").trim(), "deepseek-chat",
                systemContent, userContent, temperature, maxTokens);
    }

    private String doCompleteChat(String url, String key, String model, String systemContent, String userContent,
                                  double temperature, int maxTokens) throws IOException {
        if (key.isEmpty()) {
            throw new IllegalStateException("API Key 未配置");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemContent);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);

        requestBody.put("messages", new Object[]{systemMessage, userMessage});
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(requestBody),
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .post(body)
            .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody rb = response.body();
            String text = rb != null ? rb.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(model + "请求失败: HTTP " + response.code()
                    + (text.isBlank() ? "" : " - " + truncate(text)));
            }
            // 非流式返回的内容在 choices[0].message.content
            JsonNode root = objectMapper.readTree(text);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new IOException(model + " 响应缺少 choices");
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || !message.has("content")) {
                throw new IOException(model + " 响应缺少 message.content");
            }
            return message.get("content").asText("");
        }
    }

    /** 报错日志里别塞整个响应体，截一段够定位就行 */
    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private String buildSystemPrompt(String modelName, String dynasty, String location) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个专业的古建筑知识专家，精通中国古代建筑的历史、结构、风格等方面的知识。");
        systemPrompt.append("请基于以下上下文信息回答用户的问题，提供准确、详细、专业的解答。");

        // 把当前模型的元信息塞进 system prompt，回答能贴着当前模型来，不容易答偏
        if (modelName != null) {
            systemPrompt.append("\n当前浏览的模型：").append(modelName);
        }
        if (dynasty != null) {
            systemPrompt.append("\n朝代：").append(dynasty);
        }
        if (location != null) {
            systemPrompt.append("\n地理位置：").append(location);
        }

        systemPrompt.append("\n\n请以专业、客观的态度回答问题，避免猜测和错误信息。");
        systemPrompt.append("如果不确定答案，应该如实告知。");

        return systemPrompt.toString();
    }

    public interface DeepSeekCallback {
        void onChunk(String chunk);
        void onComplete();
        void onError(Exception e);
    }
}

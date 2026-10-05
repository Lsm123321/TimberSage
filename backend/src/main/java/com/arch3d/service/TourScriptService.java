package com.arch3d.service;

import com.arch3d.client.DeepSeekClient;
import com.arch3d.dto.TourScriptResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 语音导览脚本服务。
 * 用 DeepSeek 非流式接口生成结构化分章导览脚本（旁白 + 镜头 + 可选构件高亮），
 * 结果按模型 ID 缓存到 Redis，避免重复生成消耗 token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourScriptService {

    private final DeepSeekClient deepSeekClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "tour:script:";
    /** 缓存 24 小时；模型信息更新后可由管理端清理或自然过期 */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * 生成（或命中缓存）导览脚本。
     *
     * @param modelName  模型名称
     * @param dynasty    朝代
     * @param location   地理位置
     * @param description 模型描述
     * @param partNames  前端上报的真实构件名清单（mesh 命名），供 LLM 规划高亮镜头
     */
    public TourScriptResponse getOrGenerateScript(String modelName, String dynasty,
                                                  String location, String description,
                                                  List<String> partNames) {
        String cacheKey = CACHE_KEY_PREFIX + modelName;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof TourScriptResponse) {
                return (TourScriptResponse) cached;
            }
        } catch (Exception e) {
            // Redis 不可用不影响主流程，直接走生成
            log.warn("导览脚本缓存读取失败，将直接生成: {}", e.getMessage());
        }

        TourScriptResponse script = generateWithFallback(modelName, dynasty, location, description, partNames);
        try {
            redisTemplate.opsForValue().set(cacheKey, script, CACHE_TTL);
        } catch (Exception e) {
            log.warn("导览脚本缓存写入失败: {}", e.getMessage());
        }
        return script;
    }

    /** 先尝试 LLM 生成，解析失败再退回内置模板，保证接口总能返回可用脚本 */
    private TourScriptResponse generateWithFallback(String modelName, String dynasty,
                                                    String location, String description,
                                                    List<String> partNames) {
        try {
            String content = deepSeekClient.completeChat(
                buildSystemPrompt(),
                buildUserPrompt(modelName, dynasty, location, description, partNames),
                0.5, 3000);
            TourScriptResponse parsed = parseScript(content, modelName);
            if (parsed != null && parsed.getChapters() != null && !parsed.getChapters().isEmpty()) {
                return parsed;
            }
            log.warn("LLM 返回的导览脚本解析为空，使用内置模板");
        } catch (Exception e) {
            log.error("生成导览脚本失败，使用内置模板", e);
        }
        return fallbackScript(modelName);
    }

    private String buildSystemPrompt() {
        return """
                你是古建筑 3D 语音导览的编导。你会拿到一座建筑模型的基本信息和可选的构件名称清单。\
                请规划一份 6~8 章的语音导览脚本，并且像纪录片导演一样编排镜头与模型动作：\
                开场用整体概览 → 爆炸分解讲构造关系（关键章）→ 收回爆炸 → 推近特写讲细部 → 环绕展示 → 总结收尾。\
                要求：
                1. 严格只输出 JSON 数组，不要任何解释文字或 markdown 代码块标记。
                2. 数组每个元素形如：
                   {"chapter":"章节标题(<=8字)","narration":"旁白文案(60~140字，口语化、生动、适合朗读)","partName":"可选，本章高亮的构件名(必须从给出的构件清单中选，没有合适的就省略此字段)","cameraPos":[x,y,z],"cameraTarget":[x,y,z],"duration":10,"action":"动作"}
                3. action 从这些值里选（每章必须给一个）：
                   - "orbit"：环绕展示（概览/总结用）
                   - "explode"：把模型爆炸分解再讲（讲构件组合关系、榫卯结构的章节用，此时 cameraPos 拉远到 7~9 便于看全分解态）
                   - "assemble"：收回爆炸恢复整体（分解讲完后收回来）
                   - "zoomIn"：推近特写某构件（细部讲解用，cameraPos 靠近 2.5~3.5，配合 partName）
                   - "zoomOut"：拉远回全景（过渡用）
                4. 坐标基于归一化模型空间：模型被缩放到最大边长约 3、中心在原点。cameraTarget 一般在 [0,0,0] 附近或指向聚焦构件。
                5. duration 为该章飞行时长（秒），8~14 之间。
                6. narration 不得出现 markdown、引号嵌套或换行符。\
                """;
    }

    private String buildUserPrompt(String modelName, String dynasty, String location,
                                   String description, List<String> partNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("模型名称：").append(safe(modelName)).append("\n");
        sb.append("朝代：").append(safe(dynasty)).append("\n");
        sb.append("地理位置：").append(safe(location)).append("\n");
        if (description != null && !description.isBlank()) {
            String desc = description.length() > 400 ? description.substring(0, 400) + "…" : description;
            sb.append("模型描述：").append(desc).append("\n");
        }
        if (partNames != null && !partNames.isEmpty()) {
            // 名字太长会撑爆 prompt，截一下
            String joined = String.join("、", partNames);
            if (joined.length() > 600) joined = joined.substring(0, 600);
            sb.append("构件名称清单（partName 只能从中选择）：").append(joined).append("\n");
        }
        sb.append("请输出导览脚本 JSON 数组。");
        return sb.toString();
    }

    /**
     * LLM 的输出不保证干净：可能裹着 ```json 围栏或者前后说明，
     * 这里抠出最外层的 [...] 再反序列化，宽容一点少踩坑。
     */
    private TourScriptResponse parseScript(String content, String modelName) {
        if (content == null || content.isBlank()) return null;
        String text = content.trim();
        // 剥掉 ```json ... ``` 围栏
        Pattern fence = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher m = fence.matcher(text);
        if (m.find()) text = m.group(1).trim();
        // 截取最外层数组
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        String arrayJson = text.substring(start, end + 1);
        try {
            JsonNode arr = objectMapper.readTree(arrayJson);
            List<TourScriptResponse.TourChapter> chapters = new ArrayList<>();
            for (JsonNode node : arr) {
                String chapter = node.path("chapter").asText(null);
                String narration = node.path("narration").asText(null);
                if (chapter == null || narration == null) continue;
                TourScriptResponse.TourChapter.TourChapterBuilder cb = TourScriptResponse.TourChapter.builder()
                    .chapter(chapter)
                    .narration(narration);
                if (node.hasNonNull("partName")) cb.partName(node.get("partName").asText());
                if (node.has("cameraPos") && node.get("cameraPos").isArray() && node.get("cameraPos").size() == 3) {
                    cb.cameraPos(toDoubleList(node.get("cameraPos")));
                }
                if (node.has("cameraTarget") && node.get("cameraTarget").isArray() && node.get("cameraTarget").size() == 3) {
                    cb.cameraTarget(toDoubleList(node.get("cameraTarget")));
                }
                if (node.hasNonNull("duration") && node.get("duration").isNumber()) {
                    cb.duration(node.get("duration").asDouble(10.0));
                }
                if (node.hasNonNull("action")) cb.action(node.get("action").asText());
                chapters.add(cb.build());
            }
            if (chapters.isEmpty()) return null;
            return TourScriptResponse.builder()
                .modelName(modelName)
                .chapters(chapters)
                .build();
        } catch (Exception e) {
            log.warn("导览脚本 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private List<Double> toDoubleList(JsonNode arr) {
        List<Double> list = new ArrayList<>(3);
        for (JsonNode n : arr) list.add(n.asDouble(0.0));
        return list;
    }

    private String safe(String s) {
        return s == null || s.isBlank() ? "未知" : s;
    }

    /** LLM 挂了就退回这套固定四章模板，坐标都是环绕机位，对归一化模型空间是安全的 */
    private TourScriptResponse fallbackScript(String modelName) {
        String name = safe(modelName);
        List<TourScriptResponse.TourChapter> chapters = List.of(
            TourScriptResponse.TourChapter.builder()
                .chapter("整体概览")
                .narration("欢迎来到智筑云览。现在呈现在您眼前的，是一座精美的中国古建筑三维模型。让我们环绕它一周，先从整体上感受古人的营造智慧。")
                .cameraPos(List.of(6.0, 3.0, 6.0)).cameraTarget(List.of(0.0, 0.0, 0.0)).duration(10.0)
                .build(),
            TourScriptResponse.TourChapter.builder()
                .chapter("正立面")
                .narration("让我们来到建筑正面。可以看到传统建筑讲究中轴对称、层次分明，屋顶的曲线与出檐的比例，正是东方美学的精髓所在。")
                .cameraPos(List.of(0.0, 2.0, 7.0)).cameraTarget(List.of(0.0, 0.5, 0.0)).duration(9.0)
                .build(),
            TourScriptResponse.TourChapter.builder()
                .chapter("细部观察")
                .narration("接下来我们靠近一些，观察建筑的细部构造。梁枋、斗拱与构件之间的榫卯连接，不需要一颗钉子，就能让整座建筑稳固矗立数百年。")
                .cameraPos(List.of(3.0, 1.5, 4.0)).cameraTarget(List.of(0.0, 0.3, 0.0)).duration(10.0)
                .build(),
            TourScriptResponse.TourChapter.builder()
                .chapter("总结")
                .narration("一座建筑，就是一部凝固的历史。" + name + "承载的不仅是砖木瓦石，更是代代相传的匠作技艺与文化记忆。感谢您的聆听，您可以继续自由探索这座模型。")
                .cameraPos(List.of(-5.0, 2.5, 5.0)).cameraTarget(List.of(0.0, 0.0, 0.0)).duration(9.0)
                .build()
        );
        return TourScriptResponse.builder().modelName(name).chapters(chapters).build();
    }
}

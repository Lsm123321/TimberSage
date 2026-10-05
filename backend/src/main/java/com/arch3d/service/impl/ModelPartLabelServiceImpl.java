package com.arch3d.service.impl;

import com.arch3d.client.DeepSeekClient;
import com.arch3d.dto.PartBlurbResponse;
import com.arch3d.dto.PartBlurbSourceItem;
import com.arch3d.dto.PartLabelRequest;
import com.arch3d.entity.BuildingModel;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.service.ModelPartLabelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelPartLabelServiceImpl implements ModelPartLabelService {

    /** 构件浮窗底部的免责声明，跟前端展示的保持一个意思 */
    private static final String PART_BLURB_DISCLAIMER =
        "以上内容由 AI 根据本平台模型库字段与构件节点信息推断生成，仅供参观与学习参考，不构成考古定论、修缮依据或学术论文引用。";

    private final DeepSeekClient deepSeekClient;
    private final BuildingModelRepository modelRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, String> labelParts(Integer modelId, PartLabelRequest request) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("模型不存在"));

        // parts 是前端传来的 glTF 节点/网格列表：id 是 three.js 侧的节点 id，technicalName 是 glTF 导出的技术名
        List<PartLabelRequest.PartItem> parts = request.getParts();
        String lines = parts.stream()
            .map(p -> p.getId() + "\t" + p.getTechnicalName())
            .collect(Collectors.joining("\n"));

        // 强制只输出 JSON，方便直接反序列化成 Map
        String system = "你是中国古建筑领域的构件命名助手。用户会提供 glTF 导出常见的英文或混合命名的网格/节点名称。"
            + "请结合名称给出简短、专业的中文构件称呼（2~10个汉字为宜）。"
            + "若名称过于模糊，给出中性但合理的称呼（如「木构件」「石构件」），不要编造具体历史信息。"
            + "输出要求：只输出一个 JSON 对象，不要 markdown 代码块，不要解释。"
            + "JSON 的键必须是用户给出的 id（原样字符串），值是对应的中文名称。";

        StringBuilder user = new StringBuilder();
        user.append("建筑模型：").append(model.getModelName() != null ? model.getModelName() : "未命名").append('\n');
        if (model.getDynasty() != null) {
            user.append("朝代：").append(model.getDynasty()).append('\n');
        }
        if (model.getLocation() != null) {
            user.append("位置：").append(model.getLocation()).append('\n');
        }
        user.append("部件列表（每行：id<TAB>技术名）：\n");
        user.append(lines);

        String raw;
        try {
            // 非流式调用，一次拿到完整的 JSON
            raw = deepSeekClient.completeChat(system, user.toString());
        } catch (Exception e) {
            log.warn("Model part label DeepSeek failed: modelId={}, msg={}", modelId, e.getMessage());
            throw new IllegalStateException("AI 命名服务暂时不可用: " + e.getMessage());
        }

        // AI 偶尔会在 JSON 外面裹点说明文字，把最外层的 {...} 抠出来再解析
        String jsonBlob = extractJsonObject(raw.trim());
        JsonNode node;
        try {
            node = objectMapper.readTree(jsonBlob);
        } catch (Exception e) {
            log.warn("Model part label JSON parse failed: modelId={}, rawPrefix={}",
                modelId, jsonBlob.length() > 200 ? jsonBlob.substring(0, 200) : jsonBlob);
            throw new IllegalStateException("AI 返回内容无法解析为 JSON，请稍后重试");
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (PartLabelRequest.PartItem p : parts) {
            JsonNode v = node.get(p.getId());
            if (v != null && v.isTextual()) {
                String label = v.asText().trim();
                if (!label.isEmpty()) {
                    out.put(p.getId(), label);
                    continue;
                }
            }
            // AI 漏了某个 id 或者给了空串时退回技术名，前端至少有东西可显示
            out.put(p.getId(), p.getTechnicalName());
        }
        return out;
    }

    @Override
    public PartBlurbResponse describePart(Integer modelId, String technicalName, String aiLabel) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("模型不存在"));

        // sources 说明这段介绍参考了哪些字段，前端展示在"依据与参考"里
        List<PartBlurbSourceItem> sources = buildPartBlurbSources(model, technicalName, aiLabel);

        String system = "你是中国古建筑领域的教学讲解员。用户会给出三维模型里某个网格的「技术节点名」以及（若有）中文称呼。"
            + "请写一段 60～140 字的纯中文说明：从**可能承担的力学传力、围护遮挡、排水通风、装饰象征**等角度择要概括；"
            + "语气客观，避免编造该具体建筑的历史事件或确切年代；若信息不足，说明「需结合现场与文献进一步判断」。"
            + "禁止捏造书名、期刊或网址作为参考文献；若未提供文献材料，勿声称引用某篇文章。"
            + "不要 markdown，不要列表符号，不要开场白，直接输出正文。";

        StringBuilder user = new StringBuilder();
        user.append("建筑模型：").append(model.getModelName() != null ? model.getModelName() : "未命名").append('\n');
        if (model.getDynasty() != null) {
            user.append("朝代：").append(model.getDynasty()).append('\n');
        }
        if (model.getLocation() != null) {
            user.append("位置：").append(model.getLocation()).append('\n');
        }
        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            String desc = model.getDescription().trim();
            if (desc.length() > 400) {
                desc = desc.substring(0, 400) + "…";
            }
            user.append("模型简介（节选）：").append(desc).append('\n');
        }
        user.append("构件技术名：").append(technicalName).append('\n');
        if (aiLabel != null && !aiLabel.isBlank()) {
            user.append("构件中文称呼：").append(aiLabel.trim()).append('\n');
        }
        user.append("请输出该构件在古建筑中可能的作用与注意点。");

        String generatedAt = Instant.now().atZone(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        try {
            // 短介绍非流式一次生成就行
            String raw = deepSeekClient.completeChat(system, user.toString(), 0.45, 900);
            String t = raw == null ? "" : raw.trim();
            if (t.length() > 600) {
                t = t.substring(0, 600) + "…";
            }
            String blurb = t.isEmpty() ? "暂无法生成介绍，请稍后重试。" : t;
            return PartBlurbResponse.builder()
                .blurb(blurb)
                .generatedAt(generatedAt)
                .sources(sources)
                .disclaimer(PART_BLURB_DISCLAIMER)
                .build();
        } catch (Exception e) {
            log.warn("Model part blurb DeepSeek failed: modelId={}, msg={}", modelId, e.getMessage());
            throw new IllegalStateException("AI 介绍服务暂时不可用: " + e.getMessage());
        }
    }

    private List<PartBlurbSourceItem> buildPartBlurbSources(
            BuildingModel model,
            String technicalName,
            String aiLabel) {

        List<PartBlurbSourceItem> sources = new ArrayList<>();

        String modelTitle = model.getModelName() != null ? model.getModelName() : "未命名";
        StringBuilder metaLine = new StringBuilder(modelTitle);
        if (model.getDynasty() != null && !model.getDynasty().isBlank()) {
            metaLine.append(" · ").append(model.getDynasty().trim());
        }
        if (model.getLocation() != null && !model.getLocation().isBlank()) {
            metaLine.append(" · ").append(model.getLocation().trim());
        }
        sources.add(PartBlurbSourceItem.builder()
            .label("模型条目")
            .detail(metaLine.toString())
            .build());

        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            String sd = model.getDescription().trim();
            String excerpt = sd.length() > 140 ? sd.substring(0, 140) + "…" : sd;
            sources.add(PartBlurbSourceItem.builder()
                .label("模型简介（节选）")
                .detail(excerpt)
                .build());
        }

        sources.add(PartBlurbSourceItem.builder()
            .label("构件节点（glTF）")
            .detail(technicalName != null ? technicalName : "")
            .build());

        if (aiLabel != null && !aiLabel.isBlank()) {
            sources.add(PartBlurbSourceItem.builder()
                .label("中文称谓（AI）")
                .detail(aiLabel.trim())
                .build());
        }

        sources.add(PartBlurbSourceItem.builder()
            .label("摘要引擎")
            .detail("DeepSeek Chat · 依据上述字段推断生成")
            .build());

        return sources;
    }

    // AI 输出不保证干净，掐头去尾只留最外层 JSON 对象
    private String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("未找到 JSON 对象");
        }
        return raw.substring(start, end + 1);
    }
}

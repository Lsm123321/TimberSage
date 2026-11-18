package com.arch3d.service;

import com.arch3d.dto.KnowledgeGraphDTO;
import com.arch3d.entity.KbEntry;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.KbEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识图谱数据组装。
 *
 * 图分三层：
 *   分类（6个）→ 模型（66个）→ 知识类型（每模型6类）
 *
 * 为什么不一次返回全量 468 个节点：
 *   虽然 ECharts 扛得住，但一次性铺开会糊成一团，且用户根本看不清。
 *   默认只给前两层（72 个节点），点某个模型时才拉它的 6 类知识，
 *   这样每一屏都是可读的。
 *
 * 配色沿用项目整体的暖木色调，6 个分类各一个色，未分类用中性灰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final KbEntryRepository kbEntryRepository;
    private final BuildingModelRepository modelRepository;

    private static final int STATUS_APPROVED = 1;

    /**
     * 分类配色。
     * 按建筑类型给的语义色：桥梁偏青蓝（水、石），园林偏绿，民居偏土黄，
     * 宫殿偏朱红，寺庙偏橙金，未分类用灰避免抢眼。
     */
    private static final Map<String, String> CATEGORY_COLORS = Map.of(
            "古桥梁", "#4a7a90",
            "园林建筑", "#5e8c61",
            "民居建筑", "#a8895c",
            "宫殿建筑", "#a8443c",
            "寺庙建筑", "#c08a3e",
            "未分类", "#8f8880"
    );
    private static final String FALLBACK_COLOR = "#8f8880";

    /** 知识类型的中文名和展示顺序 */
    private static final LinkedHashMap<String, String> TYPE_LABELS = new LinkedHashMap<>() {{
        put("structure", "结构");
        put("material", "材料");
        put("history", "历史");
        put("culture", "文化");
        put("aesthetic", "审美");
        put("compare", "对比");
    }};

    /** 前端展示用：类型 key → 中文 */
    public static String typeLabel(String key) {
        return TYPE_LABELS.getOrDefault(key, key);
    }

    /**
     * 图谱主数据。
     *
     * @param categoryId 非空时只返回该分类下的模型
     * @param withTypes  是否把每个模型的知识类型节点也展开
     */
    public KnowledgeGraphDTO buildGraph(Integer categoryId, boolean withTypes) {
        // 一次聚合查询拿全部：模型 + 分类 + 知识条数 + 命中数。
        // 不要在这里 for 循环逐个模型查（66 次 SQL），66 个模型只差一点就变慢。
        List<Map<String, Object>> rows = modelRepository.aggregateForGraph();

        // 收集出现的分类，按模型数从多到少排，未分类永远排最后
        Map<String, Long> catModelCount = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String cn = String.valueOf(r.get("category_name"));
            catModelCount.merge(cn, 1L, Long::sum);
        }
        List<String> catNames = new ArrayList<>(catModelCount.keySet());
        catNames.sort(Comparator
                .comparing((String n) -> "未分类".equals(n) ? 1 : 0)
                .thenComparing(n -> -catModelCount.getOrDefault(n, 0L))
                .thenComparing(n -> n));

        List<KnowledgeGraphDTO.Category> categories = catNames.stream()
                .map(n -> KnowledgeGraphDTO.Category.builder()
                        .name(n)
                        .color(CATEGORY_COLORS.getOrDefault(n, FALLBACK_COLOR))
                        .build())
                .toList();
        Map<String, Integer> catIndex = new HashMap<>();
        for (int i = 0; i < catNames.size(); i++) {
            catIndex.put(catNames.get(i), i);
        }

        // 分类节点的大小按它下面模型数缩放，太小的图里看不清
        List<KnowledgeGraphDTO.Node> nodes = new ArrayList<>();
        for (String cn : catNames) {
            long cnt = catModelCount.getOrDefault(cn, 0L);
            nodes.add(KnowledgeGraphDTO.Node.builder()
                    .name(cn)
                    .nodeType("category")
                    .category(catIndex.get(cn))
                    .symbolSize((int) Math.min(78, 44 + cnt * 1.2))
                    .value((int) cnt)
                    .build());
        }

        List<KnowledgeGraphDTO.Link> links = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            Integer cid = ((Number) r.get("category_id_or0")).intValue();
            if (categoryId != null && !categoryId.equals(cid)) {
                continue;
            }
            String cn = String.valueOf(r.get("category_name"));
            String modelName = String.valueOf(r.get("model_name"));
            Integer modelId = ((Number) r.get("model_id")).intValue();
            int entryCount = ((Number) r.get("entry_count")).intValue();
            int hitCount = ((Number) r.get("total_hits")).intValue();

            // 节点大小：知识条数决定基础值，命中多的额外放大一点
            int size = (int) Math.min(46, 22 + entryCount * 0.8 + Math.min(hitCount, 20) * 0.6);

            nodes.add(KnowledgeGraphDTO.Node.builder()
                    .name(modelName)
                    .nodeType("model")
                    .modelId(modelId)
                    .category(catIndex.getOrDefault(cn, 0))
                    .symbolSize(size)
                    .value(hitCount > 0 ? hitCount : entryCount)
                    .entryCount(entryCount)
                    .hitCount(hitCount)
                    .hot(hitCount > 0)
                    .build());

            links.add(KnowledgeGraphDTO.Link.builder()
                    .source(cn)
                    .target(modelName)
                    .value(entryCount)
                    .build());

            // 三层展开：把该模型的 6 类知识作为末端节点挂上去
            if (withTypes) {
                for (Map.Entry<String, String> t : TYPE_LABELS.entrySet()) {
                    String typeNodeName = modelName + " · " + t.getValue();
                    nodes.add(KnowledgeGraphDTO.Node.builder()
                            .name(typeNodeName)
                            .nodeType("questionType")
                            .modelId(modelId)
                            .questionType(t.getKey())
                            .category(catIndex.getOrDefault(cn, 0))
                            .symbolSize(13)
                            .value(hitCount)
                            .build());
                    links.add(KnowledgeGraphDTO.Link.builder()
                            .source(modelName)
                            .target(typeNodeName)
                            .value(1)
                            .build());
                }
            }
        }

        return KnowledgeGraphDTO.builder()
                .categories(categories)
                .nodes(nodes)
                .links(links)
                .stats(buildStats())
                .build();
    }

    /**
     * 单个模型的展开数据。
     * 点击模型节点时按需拉取，比一次全量返回省一半以上传输量。
     */
    public KnowledgeGraphDTO expandModel(Integer modelId) {
        var model = modelRepository.findById(modelId).orElse(null);
        if (model == null) {
            return KnowledgeGraphDTO.builder()
                    .categories(List.of()).nodes(List.of()).links(List.of())
                    .stats(buildStats()).build();
        }
        List<KbEntry> entries = kbEntryRepository.findByModelIdAndStatus(modelId, STATUS_APPROVED);

        List<KnowledgeGraphDTO.Node> nodes = new ArrayList<>();
        List<KnowledgeGraphDTO.Link> links = new ArrayList<>();
        String modelName = model.getModelName();

        for (Map.Entry<String, String> t : TYPE_LABELS.entrySet()) {
            List<KbEntry> ofType = entries.stream()
                    .filter(e -> t.getKey().equals(e.getQuestionType()))
                    .toList();
            if (ofType.isEmpty()) {
                continue;
            }
            int hits = ofType.stream().mapToInt(e -> e.getHitCount() == null ? 0 : e.getHitCount()).sum();
            String name = modelName + " · " + t.getValue();
            nodes.add(KnowledgeGraphDTO.Node.builder()
                    .name(name)
                    .nodeType("questionType")
                    .modelId(modelId)
                    .questionType(t.getKey())
                    .symbolSize((int) Math.min(30, 13 + hits * 1.5))
                    .value(hits)
                    .entryCount(ofType.size())
                    .hitCount(hits)
                    .hot(hits > 0)
                    .build());
            links.add(KnowledgeGraphDTO.Link.builder()
                    .source(modelName).target(name).value(ofType.size()).build());
        }

        return KnowledgeGraphDTO.builder()
                .categories(List.of())
                .nodes(nodes)
                .links(links)
                .stats(buildStats())
                .build();
    }

    /** 统计块，图谱页右侧和顶部都用得到 */
    public KnowledgeGraphDTO.Stats buildStats() {
        long entries = kbEntryRepository.count();
        long approved = kbEntryRepository.countByStatus(STATUS_APPROVED);
        long candidate = kbEntryRepository.countByStatus(0);
        long models = modelRepository.count();
        long hits = kbEntryRepository.sumHitCount();

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Map<String, Object> r : kbEntryRepository.countGroupByType()) {
            String key = String.valueOf(r.get("question_type"));
            byType.put(TYPE_LABELS.getOrDefault(key, key),
                    ((Number) r.get("cnt")).longValue());
        }

        return KnowledgeGraphDTO.Stats.builder()
                .models(models)
                .entries(entries)
                .approved(approved)
                .candidate(candidate)
                .totalHits(hits)
                .byType(byType)
                .build();
    }
}

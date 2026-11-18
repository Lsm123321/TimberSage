package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱数据。
 *
 * 图的结构是三层：分类 → 模型 → 知识类型。
 * 默认只返回前两层（72 个节点），展开某个模型时才把它的 6 类知识加进来，
 * 这样首屏渲染压力最小，视觉上也更干净。
 *
 * 为什么要专门做一个 DTO 而不是直接返回实体：
 * ECharts graph 要求的字段是 nodes/links/categories，
 * 和库表结构没有一一对应关系，需要聚合和重命名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphDTO {

    /** 分类图例，ECharts 靠它给节点上色 */
    private List<Category> categories;

    private List<Node> nodes;

    private List<Link> links;

    private Stats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private String name;
        /** 十六进制色值，前端直接用 */
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        /** ECharts 用 name 做节点唯一标识，实测 66 个模型名无重复 */
        private String name;
        private Integer symbolSize;
        /** 对应 categories 数组的下标 */
        private Integer category;
        /** 节点权重：有命中时用命中数，否则用知识条数 */
        private Integer value;
        /** category / model / questionType 三种 */
        private String nodeType;
        /** 模型节点才有，点击展开时用 */
        private Integer modelId;
        /** 模型节点才有 */
        private Integer entryCount;
        private Integer hitCount;
        /** 知识类型节点才有 */
        private String questionType;
        /** 有命中记录时前端加金色描边，表示这是被真实问过的知识 */
        private Boolean hot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Link {
        private String source;
        private String target;
        private Integer value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Long models;
        private Long entries;
        private Long approved;
        private Long candidate;
        private Long totalHits;
        /** 各类型的知识条数，给右侧图例用 */
        private Map<String, Long> byType;
    }
}

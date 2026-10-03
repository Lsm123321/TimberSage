package com.arch3d.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 语音导览脚本：由 DeepSeek 一次性生成，前端按章节逐段
 * 「飞行镜头 + 高亮构件 + TTS 朗读」播放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourScriptResponse {

    private String modelName;

    private List<TourChapter> chapters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TourChapter {
        /** 章节标题，如「整体概览」「拱券结构」 */
        private String chapter;
        /** 该章旁白文案（TTS 朗读 + 字幕显示） */
        private String narration;
        /**
         * 可选：本章聚焦的构件名称（须来自前端上报的构件清单，
         * 前端按名称模糊匹配 mesh 并高亮；不填则只运镜不高亮）
         */
        private String partName;
        /** 相机位置 [x, y, z]（模型已归一化到半径 3 的包围盒空间） */
        private List<Double> cameraPos;
        /** 相机注视点 [x, y, z] */
        private List<Double> cameraTarget;
        /** 建议飞行时长（秒），前端会与旁白时长取平衡 */
        private Double duration;
        /**
         * 讲解动作：
         * - explode：本章讲解时将模型爆炸分解（讲构造关系用）
         * - assemble：本章开始时收回爆炸（讲整体形态用）
         * - zoomIn：镜头推近聚焦构件（细部讲解）
         * - zoomOut：镜头拉远回到全景（总结/过渡）
         * - orbit：镜头绕模型环绕一周（整体展示）
         */
        private String action;
    }
}

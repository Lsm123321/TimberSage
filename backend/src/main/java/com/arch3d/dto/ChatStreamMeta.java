package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamMeta {
    /**
     * 置信度：high / medium / low
     */
    private String confidence;

    /**
     * 从回答中提取的链接型来源
     */
    private List<String> references;

    /**
     * 从“参考依据”段落提取的文字依据
     */
    private List<String> evidenceItems;

    /**
     * 是否未检测到可验证依据
     */
    private Boolean noEvidence;

    /**
     * 元数据生成时间（yyyy-MM-dd HH:mm:ss）
     */
    private String generatedAt;

    /**
     * 知识库来源。非空表示这次回答直接来自审核过的知识档案，不是大模型现编的。
     * 前端据此展示「来自知识库」标签——这是建立信任的关键信息。
     * 为空时前端按原逻辑只显示置信度和参考来源，行为与改造前一致。
     */
    private KbSource kbSource;

    /**
     * 未能回答时的原因，非空表示前端应展示兜底卡片。
     * OUT_OF_SCOPE = 超出知识档案范围（造价、抗震、面数这类定量问题）
     * GENERAL      = 与本模型无关的通识问题
     * 为空表示正常走大模型或已命中知识库。
     */
    private String fallbackReason;

    /**
     * 兜底时给用户的建议问法，取自该模型命中次数最高的几条知识。
     */
    private List<String> suggestedQuestions;
}

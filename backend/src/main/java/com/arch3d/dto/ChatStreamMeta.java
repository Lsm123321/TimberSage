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
}

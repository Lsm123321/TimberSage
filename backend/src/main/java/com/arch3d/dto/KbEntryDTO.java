package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识条目在管理端和检索结果里的展示形态。
 * 不直接返回实体，避免把模型的 LAZY 关联和内部字段暴露出去。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbEntryDTO {
    private Integer id;
    private Integer modelId;
    private String modelName;
    private Integer categoryId;
    private String categoryName;
    private String question;
    private String normalizedQuestion;
    private String answer;
    private String answerSource;
    private String questionType;
    private Integer status;
    private Integer hitCount;
    private Integer helpfulCount;
    private Integer tokensSaved;
    private Integer reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private Integer knowledgeVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetQuestionResponse {
    private Integer id;
    private Integer categoryId;
    private Integer modelId;
    private String questionText;
    private String answerText;
    private Integer sortOrder;
    private Integer clickCount;
    private Boolean enabled;
    /** 展示场景：all / chat / model */
    private String scene;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

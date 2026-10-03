package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetQuestionUpdateRequest {
    private Integer categoryId;
    private Integer modelId;
    private String questionText;
    private String answerText;
    private Integer sortOrder;
    private Boolean enabled;
}

package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetQuestionCreateRequest {
    private Integer categoryId;
    private Integer modelId;
    
    @jakarta.validation.constraints.NotBlank(message = "问题文本不能为空")
    private String questionText;

    // 兼容历史字段：前端不再要求录入答案，后端会在创建时自动补默认值
    private String answerText;
    
    private Integer sortOrder;
    private Boolean enabled;
}

package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelFeedbackCreateRequest {
    @NotNull(message = "模型ID不能为空")
    private Integer modelId;
    
    @NotBlank(message = "错误类型不能为空")
    private String errorType;
    
    @NotBlank(message = "错误描述不能为空")
    private String description;
    
    private String attachments;
}

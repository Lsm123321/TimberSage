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
public class HelpFeedbackCreateRequest {
    @NotNull(message = "反馈类型不能为空")
    private String feedbackType;
    
    @NotBlank(message = "反馈内容不能为空")
    private String content;
    
    private String contact;
    
    private String attachments;
}

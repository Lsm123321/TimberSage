package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteFeedbackCreateRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题过长")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容过长")
    private String content;

    @Size(max = 128, message = "联系方式过长")
    private String contact;
}


package com.arch3d.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteFeedbackUpdateRequest {
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Size(max = 5000, message = "回复过长")
    private String adminReply;
}


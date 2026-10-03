package com.arch3d.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatFeedbackRequest {
    @NotNull(message = "评价不能为空")
    @Pattern(regexp = "^[01]$", message = "评价必须是0或1")
    private Integer helpful;
}

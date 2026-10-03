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
public class HelpFeedbackResponse {
    private Integer id;
    private Integer userId;
    private String username;
    private String feedbackType;
    private String content;
    private String contact;
    private String attachments;
    private Integer status;
    private String statusText;
    private String adminRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

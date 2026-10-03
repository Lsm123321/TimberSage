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
public class SiteFeedbackResponse {
    private Integer id;
    private Integer userId;
    private String username;
    private String title;
    private String content;
    private String contact;
    private Integer status;
    private String adminReply;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


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
public class ModelFeedbackResponse {
    private Integer id;
    private Integer modelId;
    private String modelName;
    private Integer userId;
    private String username;
    private String errorType;
    private String description;
    private String attachments;
    private Integer status;
    private String statusText;
    private String adminRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

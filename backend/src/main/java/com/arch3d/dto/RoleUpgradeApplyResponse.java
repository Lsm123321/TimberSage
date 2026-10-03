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
public class RoleUpgradeApplyResponse {
    private Integer id;
    private Integer userId;
    private String username;
    private String fromRole;
    private String toRole;
    private String description;
    private Integer status;
    private String reviewRemark;
    private Integer reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


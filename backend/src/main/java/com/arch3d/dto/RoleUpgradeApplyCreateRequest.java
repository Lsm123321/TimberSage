package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleUpgradeApplyCreateRequest {
    /**
     * 申请说明
     */
    @NotBlank(message = "申请说明不能为空")
    @Size(max = 2000, message = "申请说明过长")
    private String description;
}


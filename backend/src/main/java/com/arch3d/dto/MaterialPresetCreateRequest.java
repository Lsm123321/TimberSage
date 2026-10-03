package com.arch3d.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialPresetCreateRequest {
    @NotBlank(message = "方案名称不能为空")
    private String presetName;

    private Integer modelId;

    @NotBlank(message = "材质参数不能为空")
    private String materialParams;
}

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
public class MaterialPresetResponse {
    private Integer id;
    private String presetName;
    private Integer modelId;
    private String materialParams;
    private Integer userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

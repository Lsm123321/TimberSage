package com.arch3d.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelUpdateRequest {
    private String modelName;
    private Integer categoryId;
    private String dynasty;
    private String location;
    private String description;
    private String tags;
    private Integer downloadPermission;
    private String defaultCameraPosition;
    private String defaultCameraTarget;
}

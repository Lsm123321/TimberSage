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
public class ModelDetailResponse {
    private Integer id;
    private String modelName;
    private String modelFilePath;
    private String thumbnailPath;
    private String dynasty;
    private String location;
    private String description;
    private String tags;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer likeCount;
    private Integer downloadCount;
    private Integer downloadPermission;
    private String defaultCameraPosition;
    private String defaultCameraTarget;
    private Integer status;
    private String categoryName;
    private String categoryCode;
    private String creatorUsername;
    private Integer creatorId;
    private Boolean isLiked;
    private Boolean isFavorite;
    private Integer downloadPermissionType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
public class ModelListItemResponse {
    private Integer id;
    private String modelName;
    private String thumbnailPath;
    private String dynasty;
    private String location;
    private String description;
    private Integer viewCount;
    private Integer likeCount;
    private Integer downloadCount;
    private Integer categoryId;
    private String categoryName;
    private String categoryCode;
    private String creatorUsername;
    private Boolean isLiked;
    private Boolean isFavorite;
    private Integer status;
    private String rejectReason;
    private LocalDateTime createdAt;
}

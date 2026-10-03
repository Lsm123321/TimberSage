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
public class BuildingDTO {
    private Long id;
    private String name;
    private String nameEn;
    private String description;
    private String dynasty;
    private String location;
    private String coverImage;
    private String modelUrl;
    private String modelType;
    private String specifications;
    private String historicalSignificance;
    private Integer viewCount;
    private Integer favoriteCount;
    private Boolean isFavorite;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ancient_buildings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AncientBuilding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String nameEn;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String dynasty;

    @Column(length = 100)
    private String location;

    @Column(name = "cover_image", length = 255)
    private String coverImage;

    @Column(name = "model_url", length = 255)
    private String modelUrl;

    @Column(name = "model_type", length = 20)
    private String modelType;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(columnDefinition = "TEXT")
    private String historicalSignificance;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "favorite_count")
    @Builder.Default
    private Integer favoriteCount = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}

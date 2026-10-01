package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "building_model")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BuildingCategory category;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "model_file_path", nullable = true, length = 512)
    private String modelFilePath;

    @Column(name = "thumbnail_path", length = 512)
    private String thumbnailPath;

    @Column(name = "dynasty", length = 64)
    private String dynasty;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @Column(name = "download_permission", nullable = false)
    @Builder.Default
    private Integer downloadPermission = 1;

    @Column(name = "default_camera_position", columnDefinition = "JSON")
    private String defaultCameraPosition;

    @Column(name = "default_camera_target", columnDefinition = "JSON")
    private String defaultCameraTarget;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private Integer status = 0;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_upgrade_apply")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleUpgradeApply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "from_role", nullable = false, length = 16)
    private String fromRole;

    @Column(name = "to_role", nullable = false, length = 16)
    private String toRole;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * 0 待审 / 1 通过 / 2 驳回
     */
    @Column(name = "status", nullable = false)
    private Integer status = 0;

    @Column(name = "review_remark", length = 255)
    private String reviewRemark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

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


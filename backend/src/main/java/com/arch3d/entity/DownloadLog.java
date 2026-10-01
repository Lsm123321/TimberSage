package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "download_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "model_id", nullable = false)
    private Integer modelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BuildingModel model;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "download_time")
    private LocalDateTime downloadTime;

    @PrePersist
    protected void onCreate() {
        downloadTime = LocalDateTime.now();
    }
}

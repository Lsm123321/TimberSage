package com.arch3d.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hot_search")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotSearch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "keyword", nullable = false, length = 128)
    private String keyword;

    @Column(name = "search_count", nullable = false)
    @Builder.Default
    private Integer searchCount = 0;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

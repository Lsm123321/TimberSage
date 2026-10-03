package com.arch3d.repository;

import com.arch3d.entity.DownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {
    long countByModelId(Integer modelId);
    long countByUserId(Integer userId);
    long countByDownloadTimeAfter(java.time.LocalDateTime downloadTime);
    long countByDownloadTimeBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    List<DownloadLog> findAllByOrderByDownloadTimeDesc();
    List<DownloadLog> findByDownloadTimeBetweenOrderByDownloadTimeDesc(LocalDateTime start, LocalDateTime end);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

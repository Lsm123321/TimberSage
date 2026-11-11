package com.arch3d.repository;

import com.arch3d.entity.DownloadLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 下载明细分页查询，一次性把 user / model / category 关联带出来。
     *
     * 为什么不直接 findBy...OrderBy：
     * DownloadLog 上的 user、model 都是 LAZY，每条记录访问都会单独发一条 SQL，
     * 7600 条就是上万次查询。用 LEFT JOIN FETCH 一次查完，N+1 直接消失。
     * LEFT JOIN 而非 INNER：用户或模型被删掉时日志还要能显示（前端会标「已删除模型」）。
     *
     * 计数走 countQuery 单独写，避免 JOIN FETCH 干扰 Spring Data 的自动 count 推导。
     */
    @Query(value = "SELECT d FROM DownloadLog d "
            + "LEFT JOIN FETCH d.user "
            + "LEFT JOIN FETCH d.model m "
            + "LEFT JOIN FETCH m.category "
            + "ORDER BY d.downloadTime DESC",
            countQuery = "SELECT COUNT(d) FROM DownloadLog d")
    Page<DownloadLog> findAllWithDetails(Pageable pageable);

    /** 带日期范围的分页查询，关联处理同上 */
    @Query(value = "SELECT d FROM DownloadLog d "
            + "LEFT JOIN FETCH d.user "
            + "LEFT JOIN FETCH d.model m "
            + "LEFT JOIN FETCH m.category "
            + "WHERE d.downloadTime >= :start AND d.downloadTime < :end "
            + "ORDER BY d.downloadTime DESC",
            countQuery = "SELECT COUNT(d) FROM DownloadLog d "
                    + "WHERE d.downloadTime >= :start AND d.downloadTime < :end")
    Page<DownloadLog> findByDateRangeWithDetails(@Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 Pageable pageable);
}

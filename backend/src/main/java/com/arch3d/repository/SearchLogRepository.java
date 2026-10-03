package com.arch3d.repository;

import com.arch3d.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {
    long countByKeyword(String keyword);
    List<SearchLog> findByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}

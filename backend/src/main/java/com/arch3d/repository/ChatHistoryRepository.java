package com.arch3d.repository;

import com.arch3d.entity.ChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    Page<ChatHistory> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<ChatHistory> findByUserIdAndBuildingIdOrderByCreateTimeDesc(Long userId, Long buildingId, Pageable pageable);
}

package com.arch3d.repository;

import com.arch3d.entity.AiChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, Integer> {
    Page<AiChatHistory> findByUserIdAndSessionId(Integer userId, String sessionId, Pageable pageable);
    Page<AiChatHistory> findByUserId(Integer userId, Pageable pageable);
    Page<AiChatHistory> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
    Page<AiChatHistory> findByUserIdAndModelIsNotNullOrderByCreatedAtDesc(Integer userId, Pageable pageable);
    Page<AiChatHistory> findByModelId(Integer modelId, Pageable pageable);
    Optional<AiChatHistory> findByIdAndUserId(Integer id, Integer userId);
    long countByCreatedAtAfter(java.time.LocalDateTime createdAt);
    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

package com.arch3d.repository;

import com.arch3d.entity.ModelFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelFeedbackRepository extends JpaRepository<ModelFeedback, Integer> {
    Page<ModelFeedback> findByModelId(Integer modelId, Pageable pageable);
    Page<ModelFeedback> findByUserId(Integer userId, Pageable pageable);
    Page<ModelFeedback> findByStatus(Integer status, Pageable pageable);
    long countByStatus(Integer status);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

package com.arch3d.repository;

import com.arch3d.entity.HelpFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HelpFeedbackRepository extends JpaRepository<HelpFeedback, Integer> {
    Page<HelpFeedback> findByUserId(Integer userId, Pageable pageable);
    Page<HelpFeedback> findByFeedbackType(String feedbackType, Pageable pageable);
    Page<HelpFeedback> findByStatus(Integer status, Pageable pageable);
}

package com.arch3d.repository;

import com.arch3d.entity.SiteFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteFeedbackRepository extends JpaRepository<SiteFeedback, Integer> {
    Page<SiteFeedback> findByStatus(Integer status, Pageable pageable);
    long countByStatus(Integer status);
}


package com.arch3d.repository;

import com.arch3d.entity.ContributionApply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContributionApplyRepository extends JpaRepository<ContributionApply, Integer> {
    Optional<ContributionApply> findByUserId(Integer userId);
    Page<ContributionApply> findByStatus(Integer status, Pageable pageable);
    Page<ContributionApply> findByReviewedById(Integer reviewedById, Pageable pageable);
    long countByStatus(Integer status);
    void deleteByUserId(Integer userId);
}

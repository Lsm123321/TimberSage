package com.arch3d.repository;

import com.arch3d.entity.RoleUpgradeApply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleUpgradeApplyRepository extends JpaRepository<RoleUpgradeApply, Integer> {
    Optional<RoleUpgradeApply> findFirstByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<RoleUpgradeApply> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Integer userId, Integer status);

    Page<RoleUpgradeApply> findByStatus(Integer status, Pageable pageable);
    long countByStatus(Integer status);
}


package com.arch3d.repository;

import com.arch3d.entity.UserModelFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserModelFavoriteRepository extends JpaRepository<UserModelFavorite, Integer> {
    Optional<UserModelFavorite> findByUserIdAndModelId(Integer userId, Integer modelId);
    boolean existsByUserIdAndModelId(Integer userId, Integer modelId);
    long countByModelId(Integer modelId);
    void deleteByUserIdAndModelId(Integer userId, Integer modelId);
    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

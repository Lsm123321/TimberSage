package com.arch3d.repository;

import com.arch3d.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByUserIdAndBuildingId(Long userId, Long buildingId);

    Boolean existsByUserIdAndBuildingId(Long userId, Long buildingId);

    Page<Favorite> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    void deleteByUserIdAndBuildingId(Long userId, Long buildingId);
}

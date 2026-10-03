package com.arch3d.repository;

import com.arch3d.entity.AncientBuilding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AncientBuildingRepository extends JpaRepository<AncientBuilding, Long> {
    Page<AncientBuilding> findByNameContaining(String name, Pageable pageable);

    Page<AncientBuilding> findByDynasty(String dynasty, Pageable pageable);

    @Query("SELECT ab FROM AncientBuilding ab WHERE ab.name LIKE %:keyword% OR ab.description LIKE %:keyword%")
    Page<AncientBuilding> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Modifying
    @Query("UPDATE AncientBuilding ab SET ab.viewCount = ab.viewCount + 1 WHERE ab.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE AncientBuilding ab SET ab.favoriteCount = ab.favoriteCount + 1 WHERE ab.id = :id")
    void incrementFavoriteCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE AncientBuilding ab SET ab.favoriteCount = ab.favoriteCount - 1 WHERE ab.id = :id AND ab.favoriteCount > 0")
    void decrementFavoriteCount(@Param("id") Long id);

    List<AncientBuilding> findTop10ByOrderByViewCountDesc();
}

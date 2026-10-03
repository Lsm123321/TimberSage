package com.arch3d.repository;

import com.arch3d.entity.BuildingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface BuildingModelRepository extends JpaRepository<BuildingModel, Integer> {
    Page<BuildingModel> findByCategoryId(Integer categoryId, Pageable pageable);
    Page<BuildingModel> findByModelNameContaining(String keyword, Pageable pageable);
    Page<BuildingModel> findByDynasty(String dynasty, Pageable pageable);
    Page<BuildingModel> findByStatus(Integer status, Pageable pageable);
    Page<BuildingModel> findByCreatedById(Integer userId, Pageable pageable);

    // 非管理员视角：只看已发布(status=1)
    Page<BuildingModel> findByCategoryIdAndStatus(Integer categoryId, Integer status, Pageable pageable);
    Page<BuildingModel> findByModelNameContainingAndStatus(String keyword, Integer status, Pageable pageable);
    Page<BuildingModel> findByDynastyAndStatus(String dynasty, Integer status, Pageable pageable);

    // 普通用户/贡献者：仅可查看自己创建的待审/驳回
    Page<BuildingModel> findByStatusAndCreatedById(Integer status, Integer userId, Pageable pageable);

    @Query("""
        SELECT b FROM BuildingModel b
        WHERE (:status IS NULL OR b.status = :status)
          AND (:createdById IS NULL OR b.createdBy.id = :createdById)
          AND (:categoryId IS NULL OR b.category.id = :categoryId)
          AND (:dynasty IS NULL OR :dynasty = '' OR b.dynasty = :dynasty)
          AND (
            :keyword IS NULL OR :keyword = '' OR
            (
              (:keywordScope = 'model' AND LOWER(COALESCE(b.modelName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              OR (:keywordScope = 'dynasty' AND LOWER(COALESCE(b.dynasty, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              OR (:keywordScope = 'location' AND LOWER(COALESCE(b.location, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              OR (
                (:keywordScope IS NULL OR :keywordScope = '' OR :keywordScope = 'all')
                AND (
                  LOWER(COALESCE(b.modelName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(b.dynasty, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(b.location, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                )
              )
            )
          )
        """)
    Page<BuildingModel> searchModels(
        @Param("categoryId") Integer categoryId,
        @Param("dynasty") String dynasty,
        @Param("keyword") String keyword,
        @Param("keywordScope") String keywordScope,
        @Param("status") Integer status,
        @Param("createdById") Integer createdById,
        Pageable pageable
    );

    List<BuildingModel> findByStatusAndUpdatedAtBefore(Integer status, LocalDateTime updatedAt);

    List<BuildingModel> findTop10ByOrderByViewCountDesc();
    List<BuildingModel> findTop10ByOrderByLikeCountDesc();
    List<BuildingModel> findTop10ByOrderByDownloadCountDesc();

    List<BuildingModel> findTop10ByStatusOrderByViewCountDesc(Integer status);
    List<BuildingModel> findTop10ByStatusOrderByLikeCountDesc(Integer status);
    List<BuildingModel> findTop10ByStatusOrderByDownloadCountDesc(Integer status);

    @Modifying
    @Query("UPDATE BuildingModel b SET b.viewCount = b.viewCount + 1 WHERE b.id = :id")
    void incrementViewCount(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE BuildingModel b SET b.likeCount = b.likeCount + 1 WHERE b.id = :id")
    void incrementLikeCount(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE BuildingModel b SET b.likeCount = b.likeCount - 1 WHERE b.id = :id AND b.likeCount > 0")
    void decrementLikeCount(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE BuildingModel b SET b.downloadCount = b.downloadCount + 1 WHERE b.id = :id")
    void incrementDownloadCount(@Param("id") Integer id);

    long countByStatus(Integer status);
    long countByCreatedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);
}

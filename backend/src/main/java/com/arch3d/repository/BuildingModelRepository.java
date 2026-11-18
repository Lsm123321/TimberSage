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

    /**
     * 知识图谱聚合：一次拿全 66 个模型的分类和知识条数。
     *
     * 为什么用 nativeQuery：要 LEFT JOIN 两张表再做条件聚合，JPQL 写起来很别扭，
     * 而且这里只要几个标量字段，不涉及实体映射。
     *
     * 为什么用子查询而不是直接 JOIN 再 GROUP BY：kb_entry 一个模型有 12 条，
     * 直接 JOIN 会把 building_model 的行放大，以后再加别的表就会重复计数。
     */
    @Query(value = """
            SELECT m.id            AS model_id,
                   m.model_name    AS model_name,
                   COALESCE(m.category_id, 0) AS category_id_or0,
                   COALESCE(c.category_name, '未分类') AS category_name,
                   COALESCE(k.entry_count, 0) AS entry_count,
                   COALESCE(k.total_hits, 0)  AS total_hits
            FROM building_model m
            LEFT JOIN building_category c ON m.category_id = c.id
            LEFT JOIN (
                SELECT model_id, COUNT(*) AS entry_count, COALESCE(SUM(hit_count), 0) AS total_hits
                FROM kb_entry
                WHERE status = 1
                GROUP BY model_id
            ) k ON k.model_id = m.id
            WHERE m.status = 1
            ORDER BY COALESCE(m.category_id, 0), m.id
            """, nativeQuery = true)
    List<java.util.Map<String, Object>> aggregateForGraph();
}

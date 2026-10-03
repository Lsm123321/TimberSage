package com.arch3d.repository;

import com.arch3d.entity.ModelComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModelCommentRepository extends JpaRepository<ModelComment, Integer> {
    Page<ModelComment> findByModelIdAndParentIdIsNullAndStatus(Integer modelId, Integer status, Pageable pageable);
    Page<ModelComment> findByParentIdAndStatus(Integer parentId, Integer status, Pageable pageable);
    Page<ModelComment> findByUserIdAndStatus(Integer userId, Integer status, Pageable pageable);
    List<ModelComment> findTop3ByParentIdAndStatusOrderByCreatedAtAsc(Integer parentId, Integer status);
    long countByParentId(Integer parentId);

    @Modifying
    @Query("UPDATE ModelComment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") Integer id);

    @Modifying
    @Query("UPDATE ModelComment c SET c.likeCount = c.likeCount - 1 WHERE c.id = :id AND c.likeCount > 0")
    void decrementLikeCount(@Param("id") Integer id);

    void deleteByModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

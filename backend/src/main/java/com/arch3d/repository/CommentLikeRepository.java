package com.arch3d.repository;

import com.arch3d.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Integer> {
    Optional<CommentLike> findByCommentIdAndUserId(Integer commentId, Integer userId);
    boolean existsByCommentIdAndUserId(Integer commentId, Integer userId);
    void deleteByCommentIdAndUserId(Integer commentId, Integer userId);
    void deleteByCommentModelId(Integer modelId);
    void deleteByUserId(Integer userId);
}

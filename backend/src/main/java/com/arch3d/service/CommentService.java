package com.arch3d.service;

import com.arch3d.dto.CommentCreateRequest;
import com.arch3d.dto.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponse createComment(CommentCreateRequest request, Integer userId);
    Page<CommentResponse> getCommentsByModel(Integer modelId, Integer userId, Pageable pageable);
    Page<CommentResponse> getReplies(Integer parentId, Integer userId, Pageable pageable);
    void deleteComment(Integer commentId, Integer userId, String userRole);
    void toggleLike(Integer commentId, Integer userId);
}

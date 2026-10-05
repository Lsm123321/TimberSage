package com.arch3d.service.impl;

import com.arch3d.dto.CommentCreateRequest;
import com.arch3d.dto.CommentResponse;
import com.arch3d.entity.*;
import com.arch3d.repository.*;
import com.arch3d.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final ModelCommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final BuildingModelRepository modelRepository;
    private final UserRepository userRepository;
    private static final int TOP_REPLIES_COUNT = 3;

    @Override
    @Transactional
    public CommentResponse createComment(CommentCreateRequest request, Integer userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        BuildingModel model = modelRepository.findById(request.getModelId())
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        ModelComment comment = ModelComment.builder()
            .model(model)
            .user(user)
            .content(request.getContent())
            .likeCount(0)
            .status(1)
            .build();

        if (request.getParentId() != null) {
            ModelComment parentComment = commentRepository.findById(request.getParentId())
                .orElseThrow(() -> new RuntimeException("父评论不存在"));
            comment.setParentId(request.getParentId());
            // 回复必须挂在同一个模型的评论下面
            if (!parentComment.getModel().getId().equals(model.getId())) {
                throw new RuntimeException("回复与原评论不在同一模型下");
            }
        }

        ModelComment savedComment = commentRepository.save(comment);
        return convertToResponse(savedComment, userId, false);
    }

    @Override
    public Page<CommentResponse> getCommentsByModel(Integer modelId, Integer userId, Pageable pageable) {
        Page<ModelComment> commentPage = commentRepository.findByModelIdAndParentIdIsNullAndStatus(modelId, 1, pageable);

        List<CommentResponse> responses = commentPage.getContent().stream()
            .map(comment -> {
                // 列表页只带前 3 条子回复，多了让前端点开再拉
                List<ModelComment> topReplies = commentRepository.findTop3ByParentIdAndStatusOrderByCreatedAtAsc(comment.getId(), 1);
                List<CommentResponse> replyResponses = topReplies.stream()
                    .map(reply -> convertToResponse(reply, userId, false))
                    .collect(Collectors.toList());

                CommentResponse response = convertToResponse(comment, userId, false);
                response.setReplies(replyResponses);
                response.setHasReplies(commentRepository.countByParentId(comment.getId()) > 0);
                return response;
            })
            .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, commentPage.getTotalElements());
    }

    @Override
    public Page<CommentResponse> getReplies(Integer parentId, Integer userId, Pageable pageable) {
        Page<ModelComment> replyPage = commentRepository.findByParentIdAndStatus(parentId, 1, pageable);

        List<CommentResponse> responses = replyPage.getContent().stream()
            .map(reply -> convertToResponse(reply, userId, false))
            .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, replyPage.getTotalElements());
    }

    @Override
    @Transactional
    public void deleteComment(Integer commentId, Integer userId, String userRole) {
        ModelComment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new RuntimeException("评论不存在"));

        // 作者本人、模型贡献者、管理员三方都能删
        boolean isAuthor = comment.getUser().getId().equals(userId);
        boolean isAdmin = "admin".equals(userRole);
        boolean isModelContributor = comment.getModel().getCreatedBy().getId().equals(userId);

        if (!isAuthor && !isAdmin && !isModelContributor) {
            throw new RuntimeException("无权限删除此评论");
        }

        // 软删除，置 status=0 就查不到了
        comment.setStatus(0);
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void toggleLike(Integer commentId, Integer userId) {
        ModelComment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new RuntimeException("评论不存在"));

        var existingLike = commentLikeRepository.findByCommentIdAndUserId(commentId, userId);

        if (existingLike.isPresent()) {
            commentLikeRepository.delete(existingLike.get());
            commentRepository.decrementLikeCount(commentId);
        } else {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

            CommentLike like = CommentLike.builder()
                .comment(comment)
                .user(user)
                .build();
            commentLikeRepository.save(like);
            commentRepository.incrementLikeCount(commentId);
        }
    }

    private CommentResponse convertToResponse(ModelComment comment, Integer userId, boolean includeReplies) {
        Boolean isLiked = false;
        if (userId != null) {
            isLiked = commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId);
        }

        return CommentResponse.builder()
            .id(comment.getId())
            .modelId(comment.getModel().getId())
            .userId(comment.getUser().getId())
            .username(comment.getUser().getUsername())
            .userAvatar(comment.getUser().getAvatar())
            .parentId(comment.getParentId())
            .content(comment.getContent())
            .likeCount(comment.getLikeCount())
            .isLiked(isLiked)
            .hasReplies(false)
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
}

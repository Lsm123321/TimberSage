package com.arch3d.controller;

import com.arch3d.dto.CommentCreateRequest;
import com.arch3d.dto.CommentResponse;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "评论管理", description = "模型评论接口")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "发表评论", description = "发表一级评论或回复")
    public ResponseEntity<CommentResponse> createComment(
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        CommentResponse response = commentService.createComment(request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "获取评论列表", description = "分页获取模型的一级评论，每个评论携带前3条子回复")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @RequestParam Integer modelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String sortField = "likeCount".equalsIgnoreCase(sortBy) ? "likeCount" : "createdAt";
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(sortField).descending());
        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;

        Page<CommentResponse> comments = commentService.getCommentsByModel(modelId, userId, pageable);
        return ResponseEntity.ok(comments);
    }

    @GetMapping("/{id}/replies")
    @Operation(summary = "获取子回复", description = "获取某个评论的所有子回复")
    public ResponseEntity<Page<CommentResponse>> getReplies(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String sortField = "likeCount".equalsIgnoreCase(sortBy) ? "likeCount" : "createdAt";
        Sort sort = "likeCount".equalsIgnoreCase(sortBy)
                ? Sort.by(sortField).descending()
                : Sort.by(sortField).ascending();
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;

        Page<CommentResponse> replies = commentService.getReplies(id, userId, pageable);
        return ResponseEntity.ok(replies);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "删除评论", description = "删除评论，作者、模型贡献者、管理员可删除")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        commentService.deleteComment(id, userPrincipal.getId(), userPrincipal.getRole());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "点赞/取消点赞评论", description = "切换评论的点赞状态")
    public ResponseEntity<Void> toggleLike(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        commentService.toggleLike(id, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }
}

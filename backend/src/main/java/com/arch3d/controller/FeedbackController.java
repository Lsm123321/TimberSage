package com.arch3d.controller;

import com.arch3d.dto.HelpFeedbackCreateRequest;
import com.arch3d.dto.HelpFeedbackUpdateRequest;
import com.arch3d.dto.HelpFeedbackResponse;
import com.arch3d.dto.ModelFeedbackCreateRequest;
import com.arch3d.dto.ModelFeedbackResponse;
import com.arch3d.dto.ModelFeedbackUpdateRequest;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.FeedbackService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "反馈管理", description = "用户反馈管理接口")
public class FeedbackController {

    private final FeedbackService feedbackService;

    // 模型纠错反馈
    @PostMapping("/model-feedback")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "提交模型纠错反馈", description = "用户提交模型的错误反馈")
    public ResponseEntity<ModelFeedbackResponse> createModelFeedback(
            @Valid @RequestBody ModelFeedbackCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ModelFeedbackResponse response = feedbackService.createModelFeedback(request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/model-feedback")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取模型纠错反馈列表", description = "管理员获取所有模型纠错反馈")
    public ResponseEntity<Page<ModelFeedbackResponse>> getModelFeedbacks(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<ModelFeedbackResponse> feedbacks = feedbackService.getModelFeedbacks(status, pageable);
        return ResponseEntity.ok(feedbacks);
    }

    /**
     * 前端公开纠错列表：只返回管理员已采纳的记录。
     * 待审核、已驳回的提交仅本人和管理员可见，
     * 避免未经核实的内容直接出现在模型详情页被其他用户看到。
     */
    @GetMapping("/models/{modelId}/feedback")
    @Operation(summary = "获取模型已采纳的纠错", description = "仅返回审核通过的纠错记录")
    public ResponseEntity<Page<ModelFeedbackResponse>> getPublishedModelFeedbacks(
            @PathVariable Integer modelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(feedbackService.getPublishedModelFeedbacks(modelId, pageable));
    }

    /**
     * 当前用户在该模型下提交的反馈（含待审核）。
     * 用 isAuthenticated 而不是匿名可访问：只有本人能看自己的提交记录。
     */
    @GetMapping("/models/{modelId}/feedback/mine")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取我的模型纠错提交", description = "含待审核状态，仅本人可见")
    public ResponseEntity<Page<ModelFeedbackResponse>> getMyModelFeedbacks(
            @PathVariable Integer modelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                feedbackService.getMyModelFeedbacks(modelId, userPrincipal.getId(), pageable));
    }

    @PutMapping("/admin/model-feedback/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "处理模型纠错反馈", description = "管理员处理（采纳/驳回）模型纠错反馈")
    public ResponseEntity<ModelFeedbackResponse> updateModelFeedback(
            @PathVariable Integer id,
            @Valid @RequestBody ModelFeedbackUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ModelFeedbackResponse response = feedbackService.updateModelFeedback(id, request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    // 帮助反馈
    @PostMapping("/help-feedback")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "提交帮助反馈", description = "用户提交功能建议、Bug报告等反馈")
    public ResponseEntity<HelpFeedbackResponse> createHelpFeedback(
            @Valid @RequestBody HelpFeedbackCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        HelpFeedbackResponse response = feedbackService.createHelpFeedback(request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/help-feedback")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取帮助反馈列表", description = "管理员获取所有帮助反馈")
    public ResponseEntity<Page<HelpFeedbackResponse>> getHelpFeedbacks(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<HelpFeedbackResponse> feedbacks = feedbackService.getHelpFeedbacks(status, pageable);
        return ResponseEntity.ok(feedbacks);
    }

    @PutMapping("/admin/help-feedback/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "处理帮助反馈", description = "管理员处理（已处理/已忽略）帮助反馈")
    public ResponseEntity<HelpFeedbackResponse> updateHelpFeedback(
            @PathVariable Integer id,
            @Valid @RequestBody HelpFeedbackUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        HelpFeedbackResponse response = feedbackService.updateHelpFeedback(id, request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }
}

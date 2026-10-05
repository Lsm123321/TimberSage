package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.PageDTO;
import com.arch3d.dto.SiteFeedbackCreateRequest;
import com.arch3d.dto.SiteFeedbackResponse;
import com.arch3d.dto.SiteFeedbackUpdateRequest;
import com.arch3d.entity.SiteFeedback;
import com.arch3d.entity.User;
import com.arch3d.repository.SiteFeedbackRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.security.UserPrincipal;
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
@RequestMapping("/api/site-feedback")
@RequiredArgsConstructor
@Tag(name = "站点建议反馈", description = "关于我们/建议提交与后台处理")
public class SiteFeedbackController {

    private final SiteFeedbackRepository siteFeedbackRepository;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "提交站点建议", description = "允许匿名或登录用户提交建议")
    public ResponseEntity<ApiResponse<SiteFeedbackResponse>> create(
            @Valid @RequestBody SiteFeedbackCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        SiteFeedback feedback = new SiteFeedback();
        if (userPrincipal != null) {
            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            feedback.setUser(user);
        }
        feedback.setTitle(request.getTitle());
        feedback.setContent(request.getContent());
        feedback.setContact(request.getContact());
        feedback.setStatus(0);

        SiteFeedback saved = siteFeedbackRepository.save(feedback);
        return ResponseEntity.ok(ApiResponse.success("提交成功", toResponse(saved)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取站点建议列表", description = "管理员分页获取站点建议")
    public ResponseEntity<ApiResponse<PageDTO<SiteFeedbackResponse>>> adminList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<SiteFeedback> data;
        if (status != null) {
            data = siteFeedbackRepository.findByStatus(status, pageable);
        } else {
            data = siteFeedbackRepository.findAll(pageable);
        }

        PageDTO<SiteFeedbackResponse> pageDTO = PageDTO.<SiteFeedbackResponse>builder()
                .total(data.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(data.getContent().stream().map(this::toResponse).toList())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageDTO));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "处理站点建议", description = "管理员更新状态/回复")
    public ResponseEntity<ApiResponse<SiteFeedbackResponse>> adminUpdate(
            @PathVariable Integer id,
            @Valid @RequestBody SiteFeedbackUpdateRequest request) {

        SiteFeedback feedback = siteFeedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("反馈不存在"));

        feedback.setStatus(request.getStatus());
        feedback.setAdminReply(request.getAdminReply());

        SiteFeedback saved = siteFeedbackRepository.save(feedback);
        return ResponseEntity.ok(ApiResponse.success("更新成功", toResponse(saved)));
    }

    private SiteFeedbackResponse toResponse(SiteFeedback feedback) {
        Integer userId = null;
        String username = null;
        if (feedback.getUser() != null) {
            userId = feedback.getUser().getId();
            username = feedback.getUser().getUsername();
        }
        return SiteFeedbackResponse.builder()
                .id(feedback.getId())
                .userId(userId)
                .username(username)
                .title(feedback.getTitle())
                .content(feedback.getContent())
                .contact(feedback.getContact())
                .status(feedback.getStatus())
                .adminReply(feedback.getAdminReply())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }
}


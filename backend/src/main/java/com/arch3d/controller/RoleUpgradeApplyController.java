package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.RoleUpgradeApplyCreateRequest;
import com.arch3d.dto.RoleUpgradeApplyResponse;
import com.arch3d.entity.RoleUpgradeApply;
import com.arch3d.entity.User;
import com.arch3d.repository.RoleUpgradeApplyRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role-upgrade-applies")
@RequiredArgsConstructor
@Tag(name = "权限晋级申请", description = "用户申请更高权限（user→contributor / contributor→admin）")
@SecurityRequirement(name = "Bearer Authentication")
public class RoleUpgradeApplyController {

    private final RoleUpgradeApplyRepository roleUpgradeApplyRepository;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "提交权限晋级申请", description = "user 可申请 contributor；contributor 可申请 admin")
    public ResponseEntity<ApiResponse<RoleUpgradeApplyResponse>> create(
            @Valid @RequestBody RoleUpgradeApplyCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String fromRole = user.getRole();
        String toRole = resolveTargetRole(fromRole);
        if (toRole == null) {
            throw new RuntimeException("当前角色无需申请晋级");
        }

        // 已有待审申请则不允许重复提交
        if (roleUpgradeApplyRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), 0).isPresent()) {
            throw new RuntimeException("你已有待审核的申请，请耐心等待");
        }

        RoleUpgradeApply apply = new RoleUpgradeApply();
        apply.setUser(user);
        apply.setFromRole(fromRole);
        apply.setToRole(toRole);
        apply.setDescription(request.getDescription());
        apply.setStatus(0);

        RoleUpgradeApply saved = roleUpgradeApplyRepository.save(apply);
        return ResponseEntity.ok(ApiResponse.success("提交成功", toResponse(saved)));
    }

    @GetMapping("/my/latest")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取我最新的权限晋级申请", description = "返回当前用户最新一条申请（可为空）")
    public ResponseEntity<ApiResponse<RoleUpgradeApplyResponse>> getMyLatest(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        return ResponseEntity.ok(ApiResponse.success(
                roleUpgradeApplyRepository.findFirstByUserIdOrderByCreatedAtDesc(userPrincipal.getId())
                        .map(this::toResponse)
                        .orElse(null)
        ));
    }

    private String resolveTargetRole(String fromRole) {
        if ("user".equals(fromRole)) {
            return "contributor";
        }
        if ("contributor".equals(fromRole)) {
            return "admin";
        }
        return null;
    }

    private RoleUpgradeApplyResponse toResponse(RoleUpgradeApply apply) {
        Integer reviewerId = null;
        if (apply.getReviewedBy() != null) {
            reviewerId = apply.getReviewedBy().getId();
        }
        return RoleUpgradeApplyResponse.builder()
                .id(apply.getId())
                .userId(apply.getUser() != null ? apply.getUser().getId() : null)
                .username(apply.getUser() != null ? apply.getUser().getUsername() : null)
                .fromRole(apply.getFromRole())
                .toRole(apply.getToRole())
                .description(apply.getDescription())
                .status(apply.getStatus())
                .reviewRemark(apply.getReviewRemark())
                .reviewedBy(reviewerId)
                .createdAt(apply.getCreatedAt())
                .updatedAt(apply.getUpdatedAt())
                .build();
    }
}


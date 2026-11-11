package com.arch3d.controller;

import com.arch3d.dto.*;
import com.arch3d.entity.*;
import com.arch3d.repository.ContributionApplyRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.RoleUpgradeApplyRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.AvatarService;
import com.arch3d.service.MinioService;
import com.arch3d.service.StatisticsService;
import com.arch3d.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "管理后台", description = "管理员操作接口")
public class AdminController {

    private final StatisticsService statisticsService;
    private final UserRepository userRepository;
    private final ContributionApplyRepository contributionApplyRepository;
    private final RoleUpgradeApplyRepository roleUpgradeApplyRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final BuildingModelRepository buildingModelRepository;
    private final MinioService minioService;
    private final AvatarService avatarService;

    private static final String BUCKET_MODELS = "models";
    private static final String BUCKET_THUMBNAILS = "thumbnails";

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取仪表盘统计", description = "获取模型数、待审核数、今日问答量等统计信息")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard() {
        Map<String, Object> statistics = statisticsService.getDashboardStatistics();
        statistics.put("trend", statisticsService.get7DayTrend());
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取用户列表", description = "获取所有用户列表，支持分页")
    public ResponseEntity<ApiResponse<PageDTO<User>>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("id").ascending());
        Page<User> users = userRepository.findAll(pageable);
        
        PageDTO<User> pageDTO = PageDTO.<User>builder()
                .total(users.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(users.getContent())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageDTO));
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "修改用户角色", description = "修改用户的角色")
    public ResponseEntity<ApiResponse<User>> updateUserRole(
            @PathVariable Integer id,
            @RequestParam(required = false) String role,
            @RequestBody(required = false) Map<String, String> body) {

        String roleValue = role;
        if (roleValue == null && body != null) {
            roleValue = body.get("role");
        }
        if (roleValue == null || roleValue.isBlank()) {
            throw new RuntimeException("角色不能为空");
        }

        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        String normalizedTargetRole = roleValue.trim().toLowerCase();
        // 验证角色是否合法
        if (!List.of("user", "contributor", "admin").contains(normalizedTargetRole)) {
            throw new RuntimeException("无效的角色");
        }

        ensureNotDemotingLastAdmin(user, normalizedTargetRole);
        user.setRole(normalizedTargetRole);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除用户", description = "删除指定用户")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "重置用户密码", description = "重置用户密码为默认密码123456")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Integer id) {
        userService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("密码重置成功", null));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "创建用户", description = "管理员创建新用户")
    public ResponseEntity<ApiResponse<User>> createUser(@Valid @RequestBody AdminUserCreateRequest request) {
        if (userService.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (request.getEmail() != null && !request.getEmail().isEmpty() && userService.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .avatar(request.getAvatar())
                .nickname(request.getNickname())
                .role(request.getRole() != null ? request.getRole() : "user")
                .build();
        
        User savedUser = userService.saveUser(user);
        return ResponseEntity.ok(ApiResponse.success("创建成功", savedUser));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "更新用户信息", description = "更新用户基本信息（不包括密码）")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Integer id, @RequestBody UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 用户名/邮箱改了要查重，但不能跟自己撞
        if (userDTO.getUsername() != null && !userDTO.getUsername().equals(user.getUsername())) {
            if (userService.existsByUsername(userDTO.getUsername())) {
                throw new RuntimeException("用户名已存在");
            }
            user.setUsername(userDTO.getUsername());
        }

        if (userDTO.getEmail() != null && !userDTO.getEmail().equals(user.getEmail())) {
            if (userService.existsByEmail(userDTO.getEmail())) {
                throw new RuntimeException("邮箱已存在");
            }
            user.setEmail(userDTO.getEmail());
        }

        if (userDTO.getNickname() != null) {
            user.setNickname(userDTO.getNickname());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        if (userDTO.getRole() != null) {
            String normalizedTargetRole = userDTO.getRole().trim().toLowerCase();
            if (!List.of("user", "contributor", "admin").contains(normalizedTargetRole)) {
                throw new RuntimeException("无效的角色");
            }
            ensureNotDemotingLastAdmin(user, normalizedTargetRole);
            user.setRole(normalizedTargetRole);
        }
        
        User updatedUser = userService.saveUser(user);
        return ResponseEntity.ok(ApiResponse.success("更新成功", updatedUser));
    }

    /**
     * 管理员替指定用户上传头像。
     * 走独立接口而不是塞进 PUT /users/{id}，是因为 multipart 和 JSON 不能同时传。
     */
    @PostMapping(value = "/users/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "上传指定用户头像", description = "管理员为用户上传头像图片，返回可访问的 URL")
    public ResponseEntity<ApiResponse<String>> uploadUserAvatar(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String oldAvatar = user.getAvatar();

        String avatarUrl;
        try {
            avatarUrl = avatarService.uploadAvatar(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("管理员上传用户头像失败, userId={}", id, e);
            return ResponseEntity.ok(ApiResponse.error(500, "头像上传失败，请稍后再试"));
        }

        user.setAvatar(avatarUrl);
        userService.saveUser(user);
        avatarService.deleteAvatar(oldAvatar);

        return ResponseEntity.ok(ApiResponse.success("头像已更新", avatarUrl));
    }

    private void ensureNotDemotingLastAdmin(User user, String targetRole) {
        boolean isCurrentAdmin = "admin".equalsIgnoreCase(user.getRole());
        boolean isDemotingFromAdmin = isCurrentAdmin && !"admin".equalsIgnoreCase(targetRole);
        if (!isDemotingFromAdmin) {
            return;
        }

        long adminCount = userRepository.countByRoleIgnoreCase("admin");
        if (adminCount <= 1) {
            throw new RuntimeException("系统仅剩一个管理员，禁止降级该账号");
        }
    }

    @GetMapping("/contribution-applies")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取贡献者申请列表", description = "获取所有贡献者申请列表")
    public ResponseEntity<ApiResponse<PageDTO<ContributionApply>>> getContributionApplies(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<ContributionApply> applies;

        if (status != null) {
            applies = contributionApplyRepository.findByStatus(status, pageable);
        } else {
            applies = contributionApplyRepository.findAll(pageable);
        }

        PageDTO<ContributionApply> pageDTO = PageDTO.<ContributionApply>builder()
                .total(applies.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(applies.getContent())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageDTO));
    }

    @PutMapping("/contribution-applies/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "审批贡献者申请", description = "审批贡献者申请，通过或拒绝")
    public ResponseEntity<ApiResponse<ContributionApply>> updateContributionApplyStatus(
            @PathVariable Integer id,
            @RequestParam Integer status,
            @RequestParam(required = false) String reviewRemark) {

        ContributionApply apply = contributionApplyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("申请不存在"));

        apply.setStatus(status);
        if (reviewRemark != null) {
            apply.setReviewRemark(reviewRemark);
        }

        // 通过了就把用户角色升成 contributor
        if (status == 1) {
            User user = apply.getUser();
            user.setRole("contributor");
            userRepository.save(user);
        }

        contributionApplyRepository.save(apply);
        return ResponseEntity.ok(ApiResponse.success("审批成功", apply));
    }

    @GetMapping("/role-upgrade-applies")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取权限晋级申请列表", description = "获取所有权限晋级申请列表")
    public ResponseEntity<ApiResponse<PageDTO<RoleUpgradeApplyResponse>>> getRoleUpgradeApplies(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<RoleUpgradeApply> applies;

        if (status != null) {
            applies = roleUpgradeApplyRepository.findByStatus(status, pageable);
        } else {
            applies = roleUpgradeApplyRepository.findAll(pageable);
        }

        PageDTO<RoleUpgradeApplyResponse> pageDTO = PageDTO.<RoleUpgradeApplyResponse>builder()
                .total(applies.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(applies.getContent().stream().map(this::toRoleUpgradeApplyResponse).toList())
                .build();
        return ResponseEntity.ok(ApiResponse.success(pageDTO));
    }

    @PutMapping("/role-upgrade-applies/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "审批权限晋级申请", description = "审批权限晋级申请，通过或拒绝")
    public ResponseEntity<ApiResponse<RoleUpgradeApplyResponse>> updateRoleUpgradeApplyStatus(
            @PathVariable Integer id,
            @RequestParam Integer status,
            @RequestParam(required = false) String reviewRemark,
            @AuthenticationPrincipal com.arch3d.security.UserPrincipal userPrincipal) {

        RoleUpgradeApply apply = roleUpgradeApplyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("申请不存在"));

        apply.setStatus(status);
        apply.setReviewedBy(userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("审核用户不存在")));
        if (reviewRemark != null) {
            apply.setReviewRemark(reviewRemark);
        }

        // 通过了就把用户角色升成申请的目标角色
        if (status == 1) {
            User user = apply.getUser();
            user.setRole(apply.getToRole());
            userRepository.save(user);
        }

        roleUpgradeApplyRepository.save(apply);
        return ResponseEntity.ok(ApiResponse.success("审批成功", toRoleUpgradeApplyResponse(apply)));
    }

    private RoleUpgradeApplyResponse toRoleUpgradeApplyResponse(RoleUpgradeApply apply) {
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

    @GetMapping("/statistics/downloads")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取下载统计", description = "获取模型下载统计信息")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDownloadStatistics(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        List<Map<String, Object>> statistics = statisticsService.getDownloadStatistics(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    @GetMapping("/statistics/download-logs")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "获取下载明细日志", description = "分页获取下载明细日志记录")
    public ResponseEntity<ApiResponse<PageDTO<Map<String, Object>>>> getDownloadLogs(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageDTO<Map<String, Object>> logs = statisticsService.getDownloadLogs(startDate, endDate, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @DeleteMapping("/models/{id}/files")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "清理驳回模型文件", description = "删除驳回模型在MinIO中的模型文件/缩略图，并清空路径字段")
    public ResponseEntity<ApiResponse<Void>> cleanupRejectedModelFiles(@PathVariable Integer id) {
        BuildingModel model = buildingModelRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        if (model.getStatus() == null || model.getStatus() != 2) {
            throw new RuntimeException("仅支持清理已驳回(status=2)的模型文件");
        }

        String modelObject = model.getModelFilePath();
        if (modelObject != null && !modelObject.isBlank()) {
            minioService.deleteFile(BUCKET_MODELS, modelObject);
        }

        String thumbObject = model.getThumbnailPath();
        if (thumbObject != null && !thumbObject.isBlank()
            && !thumbObject.startsWith("http://") && !thumbObject.startsWith("https://")) {
            minioService.deleteFile(BUCKET_THUMBNAILS, thumbObject);
            model.setThumbnailPath(null);
        }

        buildingModelRepository.save(model);
        return ResponseEntity.ok(ApiResponse.success("清理成功", null));
    }
}

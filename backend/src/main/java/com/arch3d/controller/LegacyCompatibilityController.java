package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.entity.ChatHistory;
import com.arch3d.entity.ContributionApply;
import com.arch3d.entity.DownloadLog;
import com.arch3d.entity.HotSearch;
import com.arch3d.entity.ModelComment;
import com.arch3d.entity.User;
import com.arch3d.entity.UserMaterialPreset;
import com.arch3d.entity.UserModelFavorite;
import com.arch3d.entity.UserModelLike;
import com.arch3d.repository.BuildingCategoryRepository;
import com.arch3d.repository.ChatHistoryRepository;
import com.arch3d.repository.ContributionApplyRepository;
import com.arch3d.repository.DownloadLogRepository;
import com.arch3d.repository.HotSearchRepository;
import com.arch3d.repository.ModelCommentRepository;
import com.arch3d.repository.UserMaterialPresetRepository;
import com.arch3d.repository.UserModelFavoriteRepository;
import com.arch3d.repository.UserModelLikeRepository;
import com.arch3d.service.UserService;
import com.arch3d.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LegacyCompatibilityController {

    private final HotSearchRepository hotSearchRepository;
    private final BuildingCategoryRepository buildingCategoryRepository;
    private final ContributionApplyRepository contributionApplyRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserModelFavoriteRepository userModelFavoriteRepository;
    private final UserModelLikeRepository userModelLikeRepository;
    private final ModelCommentRepository modelCommentRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final UserMaterialPresetRepository userMaterialPresetRepository;
    private final MinioService minioService;

    @GetMapping("/hot-search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> getHotSearch() {
        LocalDate latestStatDate = hotSearchRepository.findMaxStatDate();
        List<HotSearch> list = latestStatDate == null
                ? Collections.emptyList()
                : hotSearchRepository.findTop10ByStatDateOrderBySearchCountDesc(latestStatDate);
        List<Map<String, Object>> data = list.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", item.getKeyword());
            row.put("searchCount", item.getSearchCount());
            row.put("statDate", item.getStatDate());
            return row;
        }).collect(Collectors.toList());
        return ApiResponse.success(data);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<Map<String, Object>>> getCategories() {
        List<Map<String, Object>> data = buildingCategoryRepository.findAllByOrderBySortOrderAsc().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("categoryCode", item.getCategoryCode());
            row.put("categoryName", item.getCategoryName());
            row.put("icon", item.getIcon());
            row.put("sortOrder", item.getSortOrder());
            return row;
        }).collect(Collectors.toList());
        return ApiResponse.success(data);
    }

    @PostMapping("/contribution-applies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> applyContributor(
            Authentication authentication,
            @RequestBody Map<String, String> body
    ) {
        User user = requireUser(authentication);
        String description = body.getOrDefault("description", "").trim();
        if (description.isEmpty()) {
            return ApiResponse.error(400, "申请说明不能为空");
        }

        ContributionApply apply = contributionApplyRepository.findByUserId(user.getId()).orElse(new ContributionApply());
        apply.setUser(user);
        apply.setDescription(description);
        apply.setStatus(0);
        apply.setReviewRemark(null);
        apply.setReviewedBy(null);
        ContributionApply saved = contributionApplyRepository.save(apply);

        Map<String, Object> data = new HashMap<>();
        data.put("id", saved.getId());
        data.put("status", saved.getStatus());
        return ApiResponse.success("申请已提交", data);
    }

    @GetMapping("/contribution-applies/my")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getMyContributionStatus(Authentication authentication) {
        User user = requireUser(authentication);
        ContributionApply apply = contributionApplyRepository.findByUserId(user.getId()).orElse(null);
        if (apply == null) {
            return ApiResponse.success(null);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", apply.getId());
        data.put("status", apply.getStatus());
        data.put("description", apply.getDescription());
        data.put("reviewRemark", apply.getReviewRemark());
        data.put("updatedAt", apply.getUpdatedAt());
        return ApiResponse.success(data);
    }

    @PutMapping("/users/password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> body
    ) {
        User user = requireUser(authentication);
        String oldPassword = body.getOrDefault("oldPassword", "");
        String newPassword = body.getOrDefault("newPassword", "");

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return ApiResponse.error(400, "原密码不正确");
        }
        if (newPassword.length() < 6) {
            return ApiResponse.error(400, "新密码至少6位");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userService.saveUser(user);
        return ApiResponse.success("密码修改成功", null);
    }

    @GetMapping("/users/profile/favorites")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileFavorites(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        List<Map<String, Object>> records = userModelFavoriteRepository.findAll().stream()
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .map(item -> mapModelRow(item.getModel()))
                .collect(Collectors.toList());
        return ApiResponse.success(paginate(records, page, pageSize));
    }

    @GetMapping("/users/profile/likes")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileLikes(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        List<Map<String, Object>> records = userModelLikeRepository.findAll().stream()
                .filter(item -> item.getUser().getId().equals(user.getId()))
                .map(item -> mapModelRow(item.getModel()))
                .collect(Collectors.toList());
        return ApiResponse.success(paginate(records, page, pageSize));
    }

    @GetMapping("/users/profile/comments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileComments(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        Page<ModelComment> comments = modelCommentRepository.findByUserIdAndStatus(user.getId(), 1, pageable);
        List<Map<String, Object>> records = comments.getContent().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("content", item.getContent());
            row.put("modelId", item.getModel() != null ? item.getModel().getId() : null);
            row.put("modelName", item.getModel() != null ? item.getModel().getModelName() : null);
            row.put("likeCount", item.getLikeCount());
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
        return ApiResponse.success(pageResult(records, comments.getTotalElements()));
    }

    @GetMapping("/users/profile/chat-history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileChatHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1));
        Page<ChatHistory> result = chatHistoryRepository.findByUserIdOrderByCreateTimeDesc(user.getId().longValue(), pageable);
        List<Map<String, Object>> records = result.getContent().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("question", item.getQuestion());
            row.put("answer", item.getAnswer());
            row.put("buildingId", item.getBuildingId());
            row.put("createTime", item.getCreateTime());
            return row;
        }).collect(Collectors.toList());
        return ApiResponse.success(pageResult(records, result.getTotalElements()));
    }

    @GetMapping("/users/profile/downloads")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileDownloads(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        List<Map<String, Object>> records = downloadLogRepository.findAll().stream()
                .filter(item -> item.getUserId() != null && item.getUserId().equals(user.getId()))
                .sorted((a, b) -> b.getDownloadTime().compareTo(a.getDownloadTime()))
                .map(this::mapDownloadRow)
                .collect(Collectors.toList());
        return ApiResponse.success(paginate(records, page, pageSize));
    }

    @GetMapping("/users/profile/material-presets")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> getProfileMaterialPresets(
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        User user = requireUser(authentication);
        List<Map<String, Object>> records = userMaterialPresetRepository.findByUserId(user.getId()).stream().map(this::mapPresetRow).collect(Collectors.toList());
        return ApiResponse.success(paginate(records, page, pageSize));
    }

    @GetMapping("/admin/comments")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Map<String, Object>> getAdminComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "content") String searchType,
            @RequestParam(required = false) Integer modelId
    ) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        boolean searchByUsername = "username".equalsIgnoreCase(searchType);
        List<ModelComment> all = modelCommentRepository.findAll();
        List<ModelComment> filtered = all.stream()
                .filter(item -> {
                    if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
                        return true;
                    }
                    if (searchByUsername) {
                        String username = item.getUser() != null ? item.getUser().getUsername() : null;
                        return username != null && username.contains(normalizedKeyword);
                    }
                    String content = item.getContent();
                    return content != null && content.contains(normalizedKeyword);
                })
                .filter(item -> modelId == null || (item.getModel() != null && modelId.equals(item.getModel().getId())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());

        List<Map<String, Object>> rows = filtered.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("username", item.getUser() != null ? item.getUser().getUsername() : null);
            row.put("modelName", item.getModel() != null ? item.getModel().getModelName() : null);
            row.put("content", item.getContent());
            row.put("likeCount", item.getLikeCount());
            row.put("parentId", item.getParentId());
            row.put("createdAt", item.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
        return ApiResponse.success(paginate(rows, page, pageSize));
    }

    @DeleteMapping("/admin/comments/{id}")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> deleteAdminComment(@PathVariable Integer id) {
        modelCommentRepository.deleteById(id);
        return ApiResponse.success("删除成功", null);
    }

    @DeleteMapping("/admin/comments/batch")
    @PreAuthorize("hasRole('admin')")
    public ApiResponse<Void> batchDeleteAdminComments(@RequestParam String ids) {
        if (ids == null || ids.isBlank()) {
            return ApiResponse.success("删除成功", null);
        }
        Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(item -> {
                    try {
                        modelCommentRepository.deleteById(Integer.valueOf(item));
                    } catch (NumberFormatException ignored) {
                        // 兼容性接口：允许 ids 中混入非法值，跳过即可（避免整批删除失败）
                    }
                });
        return ApiResponse.success("批量删除成功", null);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("未认证");
        }
        return userService.findByUsername(authentication.getName());
    }

    private Map<String, Object> mapModelRow(com.arch3d.entity.BuildingModel model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", model.getId());
        row.put("modelName", model.getModelName());
        row.put("thumbnailPath", resolveThumbnailUrl(model.getThumbnailPath()));
        row.put("dynasty", model.getDynasty());
        row.put("location", model.getLocation());
        row.put("categoryName", model.getCategory() != null ? model.getCategory().getCategoryName() : null);
        row.put("viewCount", model.getViewCount());
        row.put("likeCount", model.getLikeCount());
        row.put("downloadCount", model.getDownloadCount());
        row.put("modelId", model.getId());
        return row;
    }

    private String resolveThumbnailUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        if (objectName.startsWith("http://") || objectName.startsWith("https://")) {
            return objectName;
        }
        try {
            return minioService.getThumbnailUrl(objectName);
        } catch (Exception ignored) {
            return objectName;
        }
    }

    private Map<String, Object> mapDownloadRow(DownloadLog item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("modelId", item.getModelId());
        row.put("modelName", item.getModel() != null ? item.getModel().getModelName() : null);
        row.put("downloadTime", item.getDownloadTime());
        row.put("ipAddress", item.getIpAddress());
        return row;
    }

    private Map<String, Object> mapPresetRow(UserMaterialPreset item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("modelId", item.getModel() != null ? item.getModel().getId() : null);
        row.put("modelName", item.getModel() != null ? item.getModel().getModelName() : null);
        row.put("presetName", item.getPresetName());
        row.put("materialParams", item.getMaterialParams());
        row.put("createdAt", item.getCreatedAt());
        return row;
    }

    private Map<String, Object> paginate(List<Map<String, Object>> all, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(pageSize, 1);
        int from = (safePage - 1) * safeSize;
        if (from >= all.size()) {
            return pageResult(Collections.emptyList(), all.size());
        }
        int to = Math.min(from + safeSize, all.size());
        return pageResult(new ArrayList<>(all.subList(from, to)), all.size());
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> records, long total) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        return data;
    }
}

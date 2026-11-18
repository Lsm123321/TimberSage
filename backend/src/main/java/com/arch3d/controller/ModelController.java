package com.arch3d.controller;

import com.arch3d.dto.*;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.DownloadService;
import com.arch3d.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "模型管理", description = "古建筑模型管理接口")
public class ModelController {

    private final ModelService modelService;
    private final DownloadService downloadService;

    @GetMapping
    @Operation(summary = "获取模型列表", description = "分页查询模型列表，支持按分类、朝代、关键词搜索和排序")
    public ResponseEntity<Page<ModelListItemResponse>> getModels(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String dynasty,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "all") String keywordScope,
            @RequestParam(defaultValue = "viewCount") String sortBy,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(sortBy).descending());
        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;

        boolean isAdmin = userPrincipal != null && "admin".equalsIgnoreCase(userPrincipal.getRole());
        Page<ModelListItemResponse> models = modelService.getModels(
            categoryId, dynasty, keyword, keywordScope, sortBy, status, pageable, userId, isAdmin);

        return ResponseEntity.ok(models);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模型详情", description = "获取模型详细信息，包含浏览数+1")
    public ResponseEntity<ModelDetailResponse> getModelDetail(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request) {

        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;
        String clientIp = getClientIp(request);

        ModelDetailResponse model = modelService.getModelDetail(id, userId, clientIp);
        return ResponseEntity.ok(model);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('contributor', 'admin')")
    @Operation(summary = "上传模型", description = "贡献者及以上角色上传模型")
    public ResponseEntity<ModelDetailResponse> createModel(
            @RequestParam("modelFile") MultipartFile modelFile,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            @RequestParam("modelName") String modelName,
            @RequestParam(value = "categoryId", required = false) Integer categoryId,
            @RequestParam(value = "dynasty", required = false) String dynasty,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "downloadPermission", required = false, defaultValue = "1") Integer downloadPermission,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "defaultCameraPosition", required = false) String defaultCameraPosition,
            @RequestParam(value = "defaultCameraTarget", required = false) String defaultCameraTarget,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ModelCreateRequest createRequest = ModelCreateRequest.builder()
            .modelName(modelName)
            .categoryId(categoryId)
            .dynasty(dynasty)
            .location(location)
            .description(description)
            .tags(tags)
            .downloadPermission(downloadPermission)
            .status(status)
            .defaultCameraPosition(defaultCameraPosition)
            .defaultCameraTarget(defaultCameraTarget)
            .build();

        var model = modelService.createModel(modelFile, thumbnailFile, createRequest, userPrincipal.getId());

        ModelDetailResponse response = ModelDetailResponse.builder()
            .id(model.getId())
            .modelName(model.getModelName())
            .modelFilePath(model.getModelFilePath())
            .thumbnailPath(model.getThumbnailPath())
            .dynasty(model.getDynasty())
            .location(model.getLocation())
            .description(model.getDescription())
            .tags(model.getTags())
            .viewCount(model.getViewCount())
            .likeCount(model.getLikeCount())
            .downloadCount(model.getDownloadCount())
            .downloadPermission(model.getDownloadPermission())
            .status(model.getStatus())
            .createdAt(model.getCreatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('contributor', 'admin')")
    @Operation(summary = "编辑模型", description = "贡献者只能编辑自己的模型，管理员可编辑所有模型；支持替换缩略图")
    public ResponseEntity<ModelDetailResponse> updateModel(
            @PathVariable Integer id,
            @RequestParam(value = "modelName", required = false) String modelName,
            @RequestParam(value = "categoryId", required = false) Integer categoryId,
            @RequestParam(value = "dynasty", required = false) String dynasty,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "downloadPermission", required = false) Integer downloadPermission,
            @RequestParam(value = "defaultCameraPosition", required = false) String defaultCameraPosition,
            @RequestParam(value = "defaultCameraTarget", required = false) String defaultCameraTarget,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        ModelUpdateRequest updateRequest = ModelUpdateRequest.builder()
            .modelName(modelName)
            .categoryId(categoryId)
            .dynasty(dynasty)
            .location(location)
            .description(description)
            .tags(tags)
            .downloadPermission(downloadPermission)
            .defaultCameraPosition(defaultCameraPosition)
            .defaultCameraTarget(defaultCameraTarget)
            .build();

        var model = modelService.updateModel(id, updateRequest, thumbnailFile, userPrincipal.getId(), userPrincipal.getRole());

        ModelDetailResponse response = ModelDetailResponse.builder()
            .id(model.getId())
            .modelName(model.getModelName())
            .modelFilePath(model.getModelFilePath())
            .thumbnailPath(model.getThumbnailPath())
            .dynasty(model.getDynasty())
            .location(model.getLocation())
            .description(model.getDescription())
            .tags(model.getTags())
            .viewCount(model.getViewCount())
            .likeCount(model.getLikeCount())
            .downloadCount(model.getDownloadCount())
            .downloadPermission(model.getDownloadPermission())
            .status(model.getStatus())
            .createdAt(model.getCreatedAt())
            .updatedAt(model.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('contributor', 'admin')")
    @Operation(summary = "删除模型", description = "贡献者只能删除自己的模型，管理员可删除所有模型")
    public ResponseEntity<Void> deleteModel(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        modelService.deleteModel(id, userPrincipal.getId(), userPrincipal.getRole());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "修改模型状态", description = "管理员修改模型审核状态")
    public ResponseEntity<Void> updateModelStatus(
            @PathVariable Integer id,
            @Valid @RequestBody ModelStatusRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        modelService.updateModelStatus(id, request, userPrincipal.getRole());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "点赞/取消点赞", description = "切换模型的点赞状态")
    public ResponseEntity<Void> toggleLike(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        modelService.toggleLike(id, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/favorite")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "收藏模型", description = "收藏指定模型")
    public ResponseEntity<Void> addFavorite(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        modelService.addFavorite(id, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/favorite")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "取消收藏模型", description = "取消收藏指定模型")
    public ResponseEntity<Void> removeFavorite(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        modelService.removeFavorite(id, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/favorite")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "检查模型收藏状态", description = "检查当前用户是否收藏了该模型")
    public ResponseEntity<Boolean> isFavorite(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        boolean favorite = modelService.isFavorite(id, userPrincipal.getId());
        return ResponseEntity.ok(favorite);
    }

    @GetMapping("/search/suggest")
    @Operation(summary = "搜索建议", description = "根据前缀返回模型名称建议")
    public ResponseEntity<List<SearchSuggestionResponse>> getSearchSuggestions(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "10") Integer limit,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        boolean isAdmin = userPrincipal != null && "admin".equalsIgnoreCase(userPrincipal.getRole());
        List<SearchSuggestionResponse> suggestions = modelService.getSearchSuggestions(prefix, limit, isAdmin);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/hot")
    @Operation(summary = "获取热门模型", description = "获取热门模型列表")
    public ResponseEntity<List<ModelListItemResponse>> getHotModels(
            @RequestParam(defaultValue = "viewCount") String sortBy,
            @RequestParam(defaultValue = "10") Integer limit,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;
        boolean isAdmin = userPrincipal != null && "admin".equalsIgnoreCase(userPrincipal.getRole());
        List<ModelListItemResponse> hotModels = modelService.getHotModels(sortBy, limit, userId, isAdmin);
        return ResponseEntity.ok(hotModels);
    }

    @GetMapping("/{id}/file-url")
    @Operation(summary = "获取模型文件URL（预览）",
            description = "用于在线预览/管理端加载 GLB，不计入下载统计。游客可预览已发布模型；下载另走 download-url")
    public ResponseEntity<DownloadUrlResponse> getModelFileUrl(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request) {

        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;
        String clientIp = getClientIp(request);

        DownloadUrlResponse response = downloadService.getModelFileUrl(id, userId, clientIp);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取下载URL", description = "用户主动下载模型时调用，将计入下载统计")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request) {

        Integer userId = userPrincipal != null ? userPrincipal.getId() : null;
        String clientIp = getClientIp(request);

        DownloadUrlResponse response = downloadService.getDownloadUrl(id, userId, clientIp);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}

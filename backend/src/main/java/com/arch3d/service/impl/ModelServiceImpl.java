package com.arch3d.service.impl;

import com.arch3d.dto.*;
import com.arch3d.entity.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arch3d.repository.*;
import com.arch3d.service.MinioService;
import com.arch3d.service.ModelService;
import com.arch3d.service.SearchLogService;
import com.arch3d.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {

    private final BuildingModelRepository modelRepository;
    private final BuildingCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserModelLikeRepository likeRepository;
    private final UserModelFavoriteRepository favoriteRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final ModelFeedbackRepository modelFeedbackRepository;
    private final PresetQuestionRepository presetQuestionRepository;
    private final UserMaterialPresetRepository userMaterialPresetRepository;
    private final AiChatHistoryRepository aiChatHistoryRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final ModelCommentRepository modelCommentRepository;
    private final MinioService minioService;
    private final ViewCountService viewCountService;
    private final SearchLogService searchLogService;
    private final ObjectMapper objectMapper;

    private static final String BUCKET_MODELS = "models";
    private static final String BUCKET_THUMBNAILS = "thumbnails";

    @Override
    public Page<ModelListItemResponse> getModels(Integer categoryId, String dynasty, String keyword, String keywordScope,
                                                String sortBy, Integer status, Pageable pageable, Integer userId, boolean isAdmin) {
        Page<BuildingModel> modelPage;
        String normalizedScope = (keywordScope == null || keywordScope.isBlank()) ? "all" : keywordScope.trim().toLowerCase();

        // 管理员想看啥状态都行；普通人只能看已发布的，
        // 想看未发布/驳回的只限自己创建的那些
        Integer finalStatus;
        Integer createdById = null;
        if (isAdmin) {
            finalStatus = status;
        } else if (status == null || status == 1) {
            finalStatus = 1;
        } else {
            if (userId == null) {
                return Page.empty(pageable);
            }
            finalStatus = status;
            createdById = userId;
        }

        modelPage = modelRepository.searchModels(
            categoryId,
            dynasty,
            keyword,
            normalizedScope,
            finalStatus,
            createdById,
            pageable
        );

        if (keyword != null && !keyword.isBlank()) {
            searchLogService.recordSearchLog(keyword, categoryId, dynasty, userId, (int) modelPage.getTotalElements());
        }

        List<ModelListItemResponse> responses = modelPage.getContent().stream()
            .map(model -> convertToListItemResponse(model, userId))
            .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, modelPage.getTotalElements());
    }

    @Override
    @Transactional
    public ModelDetailResponse getModelDetail(Integer modelId, Integer userId, String clientIp) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        // 没发布的模型，管理员和创建者之外的人看不到
        if (model.getStatus() == null || model.getStatus() != 1) {
            boolean allowed = false;
            if (userId != null) {
                // 自己创建的待审核/被驳回的可以看
                if (model.getCreatedBy() != null && model.getCreatedBy().getId().equals(userId)) {
                    allowed = true;
                } else {
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && "admin".equalsIgnoreCase(user.getRole())) {
                        allowed = true;
                    }
                }
            }
            if (!allowed) {
                throw new RuntimeException("模型未发布或正在审核中");
            }
        }

        String viewKey = clientIp + ":" + modelId;
        if (viewCountService.canCountView(viewKey)) {
            modelRepository.incrementViewCount(modelId);
            viewCountService.recordView(viewKey);
        }

        Boolean isLiked = false;
        Boolean isFavorite = false;
        if (userId != null) {
            isLiked = likeRepository.existsByUserIdAndModelId(userId, modelId);
            isFavorite = favoriteRepository.existsByUserIdAndModelId(userId, modelId);
        }

        return convertToDetailResponse(model, isLiked, isFavorite);
    }

    @Override
    @Transactional
    public BuildingModel createModel(MultipartFile modelFile, MultipartFile thumbnailFile,
                                   ModelCreateRequest request, Integer userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (modelFile == null || modelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传3D模型文件（.glb）");
        }
        String originalFileName = modelFile.getOriginalFilename();
        if (originalFileName == null || !originalFileName.toLowerCase().endsWith(".glb")) {
            throw new IllegalArgumentException("仅支持上传 .glb 格式的3D模型文件");
        }
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String thumbnailName = thumbnailFile.getOriginalFilename();
            String lowerName = thumbnailName == null ? "" : thumbnailName.toLowerCase();
            boolean supported = lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp");
            if (!supported) {
                throw new IllegalArgumentException("缩略图仅支持 .png/.jpg/.jpeg/.webp 格式");
            }
        }

        BuildingCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        }

        String modelFilePath = null;
        String modelFileName = generateUniqueFileName(modelFile.getOriginalFilename());
        modelFilePath = minioService.uploadModelFile(modelFile, modelFileName);
        String normalizedTags = normalizeTags(request.getTags());
        Integer targetStatus;
        if ("admin".equalsIgnoreCase(user.getRole())) {
            Integer requestedStatus = request.getStatus();
            if (requestedStatus != null && requestedStatus != 0 && requestedStatus != 1) {
                throw new IllegalArgumentException("上传状态仅支持 0(待审核) 或 1(直接发布)");
            }
            targetStatus = requestedStatus != null ? requestedStatus : 1;
        } else {
            targetStatus = 0;
        }

        String thumbnailPath = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String thumbnailName = generateUniqueFileName(thumbnailFile.getOriginalFilename());
            thumbnailPath = minioService.uploadThumbnail(thumbnailFile, thumbnailName);
        }

        BuildingModel model = BuildingModel.builder()
            .modelName(request.getModelName())
            .category(category)
            .dynasty(request.getDynasty())
            .location(request.getLocation())
            .description(request.getDescription())
            .tags(normalizedTags)
            .downloadPermission(request.getDownloadPermission() != null ? request.getDownloadPermission() : 1)
            .defaultCameraPosition(request.getDefaultCameraPosition())
            .defaultCameraTarget(request.getDefaultCameraTarget())
            .modelFilePath(modelFilePath)
            .thumbnailPath(thumbnailPath)
            .viewCount(0)
            .likeCount(0)
            .downloadCount(0)
            .status(targetStatus)
            .createdBy(user)
            .build();

        return modelRepository.save(model);
    }

    @Override
    @Transactional
    public BuildingModel updateModel(Integer modelId, ModelUpdateRequest request, MultipartFile thumbnailFile,
                                     Integer userId, String userRole) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        if ("user".equals(userRole) && !model.getCreatedBy().getId().equals(userId)) {
            throw new RuntimeException("无权限编辑此模型");
        }

        if (request.getModelName() != null) {
            model.setModelName(request.getModelName());
        }
        if (request.getCategoryId() != null) {
            BuildingCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("分类不存在"));
            model.setCategory(category);
        }
        if (request.getDynasty() != null) {
            model.setDynasty(request.getDynasty());
        }
        if (request.getLocation() != null) {
            model.setLocation(request.getLocation());
        }
        if (request.getDescription() != null) {
            model.setDescription(request.getDescription());
        }
        if (request.getTags() != null) {
            model.setTags(normalizeTags(request.getTags()));
        }
        if (request.getDownloadPermission() != null) {
            model.setDownloadPermission(request.getDownloadPermission());
        }
        if (request.getDefaultCameraPosition() != null) {
            model.setDefaultCameraPosition(request.getDefaultCameraPosition());
        }
        if (request.getDefaultCameraTarget() != null) {
            model.setDefaultCameraTarget(request.getDefaultCameraTarget());
        }

        // 换缩略图：先把新图传上去再删旧图，上传失败也不至于把原图搞丢
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            String oldThumbnailPath = model.getThumbnailPath();
            String thumbnailName = generateUniqueFileName(thumbnailFile.getOriginalFilename());
            String newThumbnailPath = minioService.uploadThumbnail(thumbnailFile, thumbnailName);
            model.setThumbnailPath(newThumbnailPath);
            if (oldThumbnailPath != null && !oldThumbnailPath.isBlank()) {
                try {
                    // thumbnailPath 可能存的是完整 URL 也可能是对象名，统一取最后一段
                    String objectName = oldThumbnailPath.substring(oldThumbnailPath.lastIndexOf('/') + 1);
                    minioService.deleteFile(BUCKET_THUMBNAILS, objectName);
                } catch (Exception e) {
                    // 旧图没删掉不影响更新，记日志就行
                    log.warn("删除旧缩略图失败: {}", oldThumbnailPath, e);
                }
            }
        }

        return modelRepository.save(model);
    }

    @Override
    @Transactional
    public void deleteModel(Integer modelId, Integer userId, String userRole) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        boolean isAdmin = "admin".equalsIgnoreCase(userRole);
        boolean isOwner = model.getCreatedBy() != null && model.getCreatedBy().getId().equals(userId);
        if (!isAdmin && !isOwner) {
            throw new RuntimeException("无权限删除此模型");
        }

        // 历史库里有些表的外键策略不统一，先把引用这张模型的业务数据挨个清掉，免得删除时报外键冲突
        downloadLogRepository.deleteByModelId(modelId);
        aiChatHistoryRepository.deleteByModelId(modelId);
        presetQuestionRepository.deleteByModelId(modelId);
        userMaterialPresetRepository.deleteByModelId(modelId);
        modelFeedbackRepository.deleteByModelId(modelId);
        commentLikeRepository.deleteByCommentModelId(modelId);
        modelCommentRepository.deleteByModelId(modelId);
        favoriteRepository.deleteByModelId(modelId);
        likeRepository.deleteByModelId(modelId);

        String modelObject = model.getModelFilePath();
        if (modelObject != null && !modelObject.isBlank()) {
            minioService.deleteFile(BUCKET_MODELS, modelObject);
        }

        String thumbObject = model.getThumbnailPath();
        if (thumbObject != null
            && !thumbObject.isBlank()
            && !thumbObject.startsWith("http://")
            && !thumbObject.startsWith("https://")) {
            minioService.deleteFile(BUCKET_THUMBNAILS, thumbObject);
        }

        modelRepository.delete(model);
    }

    @Override
    @Transactional
    public void updateModelStatus(Integer modelId, ModelStatusRequest request, String userRole) {
        if (!"admin".equals(userRole)) {
            throw new RuntimeException("无权限修改模型状态");
        }

        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        model.setStatus(request.getStatus());
        if (request.getStatus() == 2 && request.getRejectReason() != null) {
            model.setRejectReason(request.getRejectReason());
        }

        modelRepository.save(model);
    }

    @Override
    @Transactional
    public void toggleLike(Integer modelId, Integer userId) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        var existingLike = likeRepository.findByUserIdAndModelId(userId, modelId);

        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
            modelRepository.decrementLikeCount(modelId);
        } else {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

            UserModelLike like = UserModelLike.builder()
                .user(user)
                .model(model)
                .build();
            likeRepository.save(like);
            modelRepository.incrementLikeCount(modelId);
        }
    }

    @Override
    @Transactional
    public void addFavorite(Integer modelId, Integer userId) {
        BuildingModel model = modelRepository.findById(modelId)
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        if (favoriteRepository.existsByUserIdAndModelId(userId, modelId)) {
            return;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        UserModelFavorite favorite = UserModelFavorite.builder()
            .user(user)
            .model(model)
            .build();
        favoriteRepository.save(favorite);
    }

    @Override
    @Transactional
    public void removeFavorite(Integer modelId, Integer userId) {
        if (!favoriteRepository.existsByUserIdAndModelId(userId, modelId)) {
            return;
        }
        favoriteRepository.deleteByUserIdAndModelId(userId, modelId);
    }

    @Override
    public boolean isFavorite(Integer modelId, Integer userId) {
        return favoriteRepository.existsByUserIdAndModelId(userId, modelId);
    }

    @Override
    public List<SearchSuggestionResponse> getSearchSuggestions(String prefix, Integer limit, boolean isAdmin) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>();
        }

        int finalLimit = limit != null ? limit : 10;
        var pageable = org.springframework.data.domain.PageRequest.of(0, finalLimit);

        Page<BuildingModel> modelPage = isAdmin
            ? modelRepository.findByModelNameContaining(prefix, pageable)
            : modelRepository.findByModelNameContainingAndStatus(prefix, 1, pageable);
        List<BuildingModel> models = modelPage.getContent();

        return models.stream()
            .map(model -> SearchSuggestionResponse.builder()
                .id(model.getId())
                .modelName(model.getModelName())
                .dynasty(model.getDynasty())
                .thumbnailPath(resolveThumbnailUrl(model.getThumbnailPath()))
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public List<ModelListItemResponse> getHotModels(String sortBy, Integer limit, Integer userId, boolean isAdmin) {
        List<BuildingModel> models;

        if ("download".equals(sortBy)) {
            models = isAdmin
                ? modelRepository.findTop10ByOrderByDownloadCountDesc()
                : modelRepository.findTop10ByStatusOrderByDownloadCountDesc(1);
        } else if ("like".equals(sortBy)) {
            models = isAdmin
                ? modelRepository.findTop10ByOrderByLikeCountDesc()
                : modelRepository.findTop10ByStatusOrderByLikeCountDesc(1);
        } else {
            models = isAdmin
                ? modelRepository.findTop10ByOrderByViewCountDesc()
                : modelRepository.findTop10ByStatusOrderByViewCountDesc(1);
        }

        final int finalLimit = limit != null ? limit : 10;
        return models.stream()
            .limit(finalLimit)
            .map(model -> convertToListItemResponse(model, userId))
            .collect(Collectors.toList());
    }

    private ModelListItemResponse convertToListItemResponse(BuildingModel model, Integer userId) {
        Boolean isLiked = false;
        Boolean isFavorite = false;

        if (userId != null) {
            isLiked = likeRepository.existsByUserIdAndModelId(userId, model.getId());
            isFavorite = favoriteRepository.existsByUserIdAndModelId(userId, model.getId());
        }

        return ModelListItemResponse.builder()
            .id(model.getId())
            .modelName(model.getModelName())
            .thumbnailPath(resolveThumbnailUrl(model.getThumbnailPath()))
            .dynasty(model.getDynasty())
            .location(model.getLocation())
            .description(model.getDescription())
            .viewCount(model.getViewCount())
            .likeCount(model.getLikeCount())
            .downloadCount(model.getDownloadCount())
            .categoryId(model.getCategory() != null ? model.getCategory().getId() : null)
            .categoryName(model.getCategory() != null ? model.getCategory().getCategoryName() : null)
            .categoryCode(model.getCategory() != null ? model.getCategory().getCategoryCode() : null)
            .creatorUsername(model.getCreatedBy() != null ? model.getCreatedBy().getUsername() : null)
            .isLiked(isLiked)
            .isFavorite(isFavorite)
            .status(model.getStatus())
            .rejectReason(model.getRejectReason())
            .createdAt(model.getCreatedAt())
            .build();
    }

    private ModelDetailResponse convertToDetailResponse(BuildingModel model, Boolean isLiked, Boolean isFavorite) {
        int favoriteCount = Math.toIntExact(favoriteRepository.countByModelId(model.getId()));
        return ModelDetailResponse.builder()
            .id(model.getId())
            .modelName(model.getModelName())
            .modelFilePath(model.getModelFilePath())
            .thumbnailPath(resolveThumbnailUrl(model.getThumbnailPath()))
            .dynasty(model.getDynasty())
            .location(model.getLocation())
            .description(model.getDescription())
            .tags(model.getTags())
            .viewCount(model.getViewCount())
            .favoriteCount(favoriteCount)
            .likeCount(model.getLikeCount())
            .downloadCount(model.getDownloadCount())
            .downloadPermission(model.getDownloadPermission())
            .defaultCameraPosition(model.getDefaultCameraPosition())
            .defaultCameraTarget(model.getDefaultCameraTarget())
            .status(model.getStatus())
            .categoryName(model.getCategory() != null ? model.getCategory().getCategoryName() : null)
            .categoryCode(model.getCategory() != null ? model.getCategory().getCategoryCode() : null)
            .creatorUsername(model.getCreatedBy() != null ? model.getCreatedBy().getUsername() : null)
            .creatorId(model.getCreatedBy() != null ? model.getCreatedBy().getId() : null)
            .isLiked(isLiked)
            .isFavorite(isFavorite)
            .downloadPermissionType(model.getDownloadPermission())
            .createdAt(model.getCreatedAt())
            .updatedAt(model.getUpdatedAt())
            .build();
    }

    private String resolveThumbnailUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        // 历史数据里有的直接存了完整 URL，这种原样返回
        if (objectName.startsWith("http://") || objectName.startsWith("https://")) {
            return objectName;
        }
        try {
            return minioService.getThumbnailUrl(objectName);
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail url for object: {}", objectName, e);
            return objectName;
        }
    }

    private String generateUniqueFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return java.util.UUID.randomUUID().toString() + extension;
    }

    private String normalizeTags(String tagsRaw) {
        if (tagsRaw == null || tagsRaw.trim().isEmpty()) {
            return null;
        }
        String trimmed = tagsRaw.trim();
        try {
            if (trimmed.startsWith("[")) {
                List<String> parsed = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                List<String> cleaned = parsed.stream()
                    .map(item -> item == null ? "" : item.trim())
                    .filter(item -> !item.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
                return cleaned.isEmpty() ? null : objectMapper.writeValueAsString(cleaned);
            }
            List<String> fromCsv = java.util.Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .collect(Collectors.toList());
            return fromCsv.isEmpty() ? null : objectMapper.writeValueAsString(fromCsv);
        } catch (Exception ex) {
            throw new IllegalArgumentException("标签格式不正确，请使用标签数组或逗号分隔字符串");
        }
    }
}

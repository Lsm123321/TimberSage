package com.arch3d.service;

import com.arch3d.dto.*;
import com.arch3d.entity.BuildingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ModelService {
    Page<ModelListItemResponse> getModels(Integer categoryId, String dynasty, String keyword, String keywordScope,
                                          String sortBy, Integer status, Pageable pageable, Integer userId, boolean isAdmin);

    ModelDetailResponse getModelDetail(Integer modelId, Integer userId, String clientIp);

    BuildingModel createModel(MultipartFile modelFile, MultipartFile thumbnailFile,
                             ModelCreateRequest request, Integer userId);

    BuildingModel updateModel(Integer modelId, ModelUpdateRequest request, MultipartFile thumbnailFile,
                             Integer userId, String userRole);

    void deleteModel(Integer modelId, Integer userId, String userRole);

    void updateModelStatus(Integer modelId, ModelStatusRequest request, String userRole);

    void toggleLike(Integer modelId, Integer userId);

    void addFavorite(Integer modelId, Integer userId);

    void removeFavorite(Integer modelId, Integer userId);

    boolean isFavorite(Integer modelId, Integer userId);

    List<SearchSuggestionResponse> getSearchSuggestions(String prefix, Integer limit, boolean isAdmin);

    List<ModelListItemResponse> getHotModels(String sortBy, Integer limit, Integer userId, boolean isAdmin);
}

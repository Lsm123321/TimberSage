package com.arch3d.service.impl;

import com.arch3d.entity.BuildingModel;
import com.arch3d.entity.User;
import com.arch3d.repository.AiChatHistoryRepository;
import com.arch3d.repository.BuildingCategoryRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.CommentLikeRepository;
import com.arch3d.repository.DownloadLogRepository;
import com.arch3d.repository.ModelCommentRepository;
import com.arch3d.repository.ModelFeedbackRepository;
import com.arch3d.repository.PresetQuestionRepository;
import com.arch3d.repository.UserMaterialPresetRepository;
import com.arch3d.repository.UserModelFavoriteRepository;
import com.arch3d.repository.UserModelLikeRepository;
import com.arch3d.repository.UserRepository;
import com.arch3d.service.MinioService;
import com.arch3d.service.SearchLogService;
import com.arch3d.service.ViewCountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceImplTest {

    @Mock
    private BuildingModelRepository modelRepository;
    @Mock
    private BuildingCategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserModelLikeRepository likeRepository;
    @Mock
    private UserModelFavoriteRepository favoriteRepository;
    @Mock
    private DownloadLogRepository downloadLogRepository;
    @Mock
    private ModelFeedbackRepository modelFeedbackRepository;
    @Mock
    private PresetQuestionRepository presetQuestionRepository;
    @Mock
    private UserMaterialPresetRepository userMaterialPresetRepository;
    @Mock
    private AiChatHistoryRepository aiChatHistoryRepository;
    @Mock
    private CommentLikeRepository commentLikeRepository;
    @Mock
    private ModelCommentRepository modelCommentRepository;
    @Mock
    private MinioService minioService;
    @Mock
    private ViewCountService viewCountService;
    @Mock
    private SearchLogService searchLogService;

    private ModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModelServiceImpl(
            modelRepository,
            categoryRepository,
            userRepository,
            likeRepository,
            favoriteRepository,
            downloadLogRepository,
            modelFeedbackRepository,
            presetQuestionRepository,
            userMaterialPresetRepository,
            aiChatHistoryRepository,
            commentLikeRepository,
            modelCommentRepository,
            minioService,
            viewCountService,
            searchLogService,
            new ObjectMapper()
        );
    }

    @Test
    void getModels_nonAdminDefaultStatus_shouldForcePublishedOnly() {
        var pageable = PageRequest.of(0, 10);
        when(modelRepository.searchModels(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty(pageable));

        var result = service.getModels(null, null, "", "all", "viewCount", null, pageable, 1001, false);

        assertEquals(0, result.getTotalElements());
        verify(modelRepository).searchModels(
            eq(null), eq(null), eq(""), eq("all"), eq(1), eq(null), eq(pageable)
        );
    }

    @Test
    void getModels_nonAdminPrivateStatusWithoutLogin_shouldReturnEmptyPage() {
        var pageable = PageRequest.of(0, 10);

        var result = service.getModels(null, null, "", "all", "viewCount", 2, pageable, null, false);

        assertTrue(result.isEmpty());
        verify(modelRepository, never()).searchModels(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getModels_withKeyword_shouldRecordSearchLog() {
        var pageable = PageRequest.of(0, 10);
        when(modelRepository.searchModels(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty(pageable));

        service.getModels(1, "宋", "石拱桥", "model", "viewCount", null, pageable, 1001, false);

        verify(searchLogService).recordSearchLog("石拱桥", 1, "宋", 1001, 0);
    }

    @Test
    void deleteModel_shouldCleanupAllRelatedDataBeforeDelete() {
        var user = new User();
        user.setId(1);
        var model = new BuildingModel();
        model.setId(10);
        model.setCreatedBy(user);
        model.setModelFilePath(null);
        model.setThumbnailPath(null);
        when(modelRepository.findById(10)).thenReturn(Optional.of(model));

        service.deleteModel(10, 1, "admin");

        verify(downloadLogRepository).deleteByModelId(10);
        verify(aiChatHistoryRepository).deleteByModelId(10);
        verify(presetQuestionRepository).deleteByModelId(10);
        verify(userMaterialPresetRepository).deleteByModelId(10);
        verify(modelFeedbackRepository).deleteByModelId(10);
        verify(commentLikeRepository).deleteByCommentModelId(10);
        verify(modelCommentRepository).deleteByModelId(10);
        verify(favoriteRepository).deleteByModelId(10);
        verify(likeRepository).deleteByModelId(10);
        verify(modelRepository).delete(model);
    }
}

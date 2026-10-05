package com.arch3d.service;

import com.arch3d.dto.HelpFeedbackCreateRequest;
import com.arch3d.dto.HelpFeedbackResponse;
import com.arch3d.dto.HelpFeedbackUpdateRequest;
import com.arch3d.dto.ModelFeedbackCreateRequest;
import com.arch3d.dto.ModelFeedbackResponse;
import com.arch3d.dto.ModelFeedbackUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeedbackService {
    ModelFeedbackResponse createModelFeedback(ModelFeedbackCreateRequest request, Integer userId);
    Page<ModelFeedbackResponse> getModelFeedbacks(Integer status, Pageable pageable);
    ModelFeedbackResponse updateModelFeedback(Integer id, ModelFeedbackUpdateRequest request, Integer adminId);

    HelpFeedbackResponse createHelpFeedback(HelpFeedbackCreateRequest request, Integer userId);
    Page<HelpFeedbackResponse> getHelpFeedbacks(Integer status, Pageable pageable);
    HelpFeedbackResponse updateHelpFeedback(Integer id, HelpFeedbackUpdateRequest request, Integer adminId);
}

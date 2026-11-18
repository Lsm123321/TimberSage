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

    /**
     * 前端公开列表：只返回管理员已采纳的纠错。
     * 待审核 / 已驳回的内容不出现在前端，避免未经核实的信息被用户看到。
     */
    Page<ModelFeedbackResponse> getPublishedModelFeedbacks(Integer modelId, Pageable pageable);

    /**
     * 当前用户在某模型下提交的反馈（含待审核），仅本人可见，用于回显自己的提交状态。
     */
    Page<ModelFeedbackResponse> getMyModelFeedbacks(Integer modelId, Integer userId, Pageable pageable);

    HelpFeedbackResponse createHelpFeedback(HelpFeedbackCreateRequest request, Integer userId);
    Page<HelpFeedbackResponse> getHelpFeedbacks(Integer status, Pageable pageable);
    HelpFeedbackResponse updateHelpFeedback(Integer id, HelpFeedbackUpdateRequest request, Integer adminId);
}

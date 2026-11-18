package com.arch3d.service.impl;

import com.arch3d.dto.HelpFeedbackCreateRequest;
import com.arch3d.dto.HelpFeedbackResponse;
import com.arch3d.dto.HelpFeedbackUpdateRequest;
import com.arch3d.dto.ModelFeedbackCreateRequest;
import com.arch3d.dto.ModelFeedbackResponse;
import com.arch3d.dto.ModelFeedbackUpdateRequest;
import com.arch3d.entity.*;
import com.arch3d.repository.*;
import com.arch3d.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final ModelFeedbackRepository modelFeedbackRepository;
    private final HelpFeedbackRepository helpFeedbackRepository;
    private final BuildingModelRepository modelRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ModelFeedbackResponse createModelFeedback(ModelFeedbackCreateRequest request, Integer userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        BuildingModel model = modelRepository.findById(request.getModelId())
            .orElseThrow(() -> new RuntimeException("模型不存在"));

        ModelFeedback feedback = ModelFeedback.builder()
            .model(model)
            .user(user)
            .errorType(request.getErrorType())
            .description(request.getDescription())
            .attachments(request.getAttachments())
            .status(0)
            .build();

        ModelFeedback saved = modelFeedbackRepository.save(feedback);
        return convertToModelFeedbackResponse(saved);
    }

    @Override
    public Page<ModelFeedbackResponse> getModelFeedbacks(Integer status, Pageable pageable) {
        Page<ModelFeedback> page;
        if (status != null) {
            page = modelFeedbackRepository.findByStatus(status, pageable);
        } else {
            page = modelFeedbackRepository.findAll(pageable);
        }
        return page.map(this::convertToModelFeedbackResponse);
    }

    /** 已采纳(1) 的纠错才对外公开；待审核和已驳回都留在后台 */
    private static final int STATUS_ADOPTED = 1;

    @Override
    public Page<ModelFeedbackResponse> getPublishedModelFeedbacks(Integer modelId, Pageable pageable) {
        Page<ModelFeedback> page = modelFeedbackRepository
            .findByModelIdAndStatus(modelId, STATUS_ADOPTED, pageable);
        // 公开列表不暴露提交者账号，用户名做打码处理
        return page.map(item -> {
            ModelFeedbackResponse response = convertToModelFeedbackResponse(item);
            response.setUsername(maskUsername(response.getUsername()));
            return response;
        });
    }

    @Override
    public Page<ModelFeedbackResponse> getMyModelFeedbacks(Integer modelId, Integer userId, Pageable pageable) {
        // 本人永远能看到自己提交的反馈（含待审核），别人看不到
        return modelFeedbackRepository
            .findByUserIdAndModelId(userId, modelId, pageable)
            .map(this::convertToModelFeedbackResponse);
    }

    /**
     * 用户名打码：保留首字符，其余用 * 代替。
     * 公开纠错记录时没必要把提交者的账号原样挂出去。
     */
    private String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return "匿名用户";
        }
        if (username.length() == 1) {
            return username + "*";
        }
        return username.charAt(0) + "**";
    }

    @Override
    @Transactional
    public ModelFeedbackResponse updateModelFeedback(Integer id, ModelFeedbackUpdateRequest request, Integer adminId) {
        ModelFeedback feedback = modelFeedbackRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("反馈不存在"));

        if (request.getStatus() != null) {
            feedback.setStatus(request.getStatus());
        }
        if (request.getAdminRemark() != null) {
            feedback.setAdminRemark(request.getAdminRemark());
        }

        if (adminId != null) {
            User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
            feedback.setReviewedBy(admin);
        }

        ModelFeedback saved = modelFeedbackRepository.save(feedback);
        return convertToModelFeedbackResponse(saved);
    }

    @Override
    @Transactional
    public HelpFeedbackResponse createHelpFeedback(HelpFeedbackCreateRequest request, Integer userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        HelpFeedback feedback = HelpFeedback.builder()
            .user(user)
            .feedbackType(request.getFeedbackType())
            .content(request.getContent())
            .contact(request.getContact())
            .attachments(request.getAttachments())
            .status(0)
            .build();

        HelpFeedback saved = helpFeedbackRepository.save(feedback);
        return convertToHelpFeedbackResponse(saved);
    }

    @Override
    public Page<HelpFeedbackResponse> getHelpFeedbacks(Integer status, Pageable pageable) {
        Page<HelpFeedback> page;
        if (status != null) {
            page = helpFeedbackRepository.findByStatus(status, pageable);
        } else {
            page = helpFeedbackRepository.findAll(pageable);
        }
        return page.map(this::convertToHelpFeedbackResponse);
    }

    @Override
    @Transactional
    public HelpFeedbackResponse updateHelpFeedback(Integer id, HelpFeedbackUpdateRequest request, Integer adminId) {
        HelpFeedback feedback = helpFeedbackRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("反馈不存在"));

        if (request.getStatus() != null) {
            feedback.setStatus(request.getStatus());
        }
        if (request.getAdminRemark() != null) {
            feedback.setAdminRemark(request.getAdminRemark());
        }

        HelpFeedback saved = helpFeedbackRepository.save(feedback);
        return convertToHelpFeedbackResponse(saved);
    }

    private ModelFeedbackResponse convertToModelFeedbackResponse(ModelFeedback feedback) {
        String statusText;
        switch (feedback.getStatus()) {
            case 0: statusText = "待处理"; break;
            case 1: statusText = "已采纳"; break;
            case 2: statusText = "已驳回"; break;
            default: statusText = "未知";
        }

        return ModelFeedbackResponse.builder()
            .id(feedback.getId())
            .modelId(feedback.getModel().getId())
            .modelName(feedback.getModel().getModelName())
            .userId(feedback.getUser().getId())
            .username(feedback.getUser().getUsername())
            .errorType(feedback.getErrorType())
            .description(feedback.getDescription())
            .attachments(feedback.getAttachments())
            .status(feedback.getStatus())
            .statusText(statusText)
            .adminRemark(feedback.getAdminRemark())
            .createdAt(feedback.getCreatedAt())
            .updatedAt(feedback.getUpdatedAt())
            .build();
    }

    private HelpFeedbackResponse convertToHelpFeedbackResponse(HelpFeedback feedback) {
        String statusText;
        switch (feedback.getStatus()) {
            case 0: statusText = "待处理"; break;
            case 1: statusText = "已处理"; break;
            case 2: statusText = "已忽略"; break;
            default: statusText = "未知";
        }

        return HelpFeedbackResponse.builder()
            .id(feedback.getId())
            .userId(feedback.getUser().getId())
            .username(feedback.getUser().getUsername())
            .feedbackType(feedback.getFeedbackType())
            .content(feedback.getContent())
            .contact(feedback.getContact())
            .attachments(feedback.getAttachments())
            .status(feedback.getStatus())
            .statusText(statusText)
            .adminRemark(feedback.getAdminRemark())
            .createdAt(feedback.getCreatedAt())
            .updatedAt(feedback.getUpdatedAt())
            .build();
    }
}

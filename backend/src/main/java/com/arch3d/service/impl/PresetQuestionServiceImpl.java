package com.arch3d.service.impl;

import com.arch3d.dto.PresetQuestionCreateRequest;
import com.arch3d.dto.PresetQuestionUpdateRequest;
import com.arch3d.dto.PresetQuestionResponse;
import com.arch3d.entity.BuildingCategory;
import com.arch3d.entity.BuildingModel;
import com.arch3d.entity.PresetQuestion;
import com.arch3d.repository.BuildingCategoryRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.repository.PresetQuestionRepository;
import com.arch3d.service.PresetQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresetQuestionServiceImpl implements PresetQuestionService {

    private final PresetQuestionRepository questionRepository;
    private final BuildingModelRepository modelRepository;
    private final BuildingCategoryRepository categoryRepository;

    @Override
    @Transactional
    public PresetQuestion createQuestion(PresetQuestionCreateRequest request) {
        BuildingModel model = null;
        if (request.getModelId() != null) {
            model = modelRepository.findById(request.getModelId())
                .orElseThrow(() -> new RuntimeException("模型不存在"));
        }

        BuildingCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("分类不存在"));
        }
        validateScope(model, category);

        PresetQuestion question = PresetQuestion.builder()
            .model(model)
            .category(category)
            .questionText(request.getQuestionText())
            // 预设问题仅用于快捷提问，答案由 AI 实时生成；这里保留占位文本以兼容历史表结构
            .answerText((request.getAnswerText() == null || request.getAnswerText().isBlank()) ? "由AI实时生成" : request.getAnswerText())
            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
            .clickCount(0)
            .enabled(request.getEnabled() != null ? (request.getEnabled() ? 1 : 0) : 1)
            .scene(normalizeScene(request.getScene()))
            .build();

        return questionRepository.save(question);
    }

    @Override
    @Transactional
    public PresetQuestion updateQuestion(Integer questionId, PresetQuestionUpdateRequest request) {
        PresetQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new RuntimeException("预设问题不存在"));

        if (request.getModelId() != null) {
            BuildingModel model = modelRepository.findById(request.getModelId())
                .orElseThrow(() -> new RuntimeException("模型不存在"));
            question.setModel(model);
        }

        if (request.getCategoryId() != null) {
            BuildingCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("分类不存在"));
            question.setCategory(category);
        }

        if (request.getQuestionText() != null) {
            question.setQuestionText(request.getQuestionText());
        }

        if (request.getAnswerText() != null) {
            question.setAnswerText(request.getAnswerText());
        }

        if (request.getSortOrder() != null) {
            question.setSortOrder(request.getSortOrder());
        }

        if (request.getEnabled() != null) {
            question.setEnabled(request.getEnabled() ? 1 : 0);
        }
        if (request.getScene() != null) {
            question.setScene(normalizeScene(request.getScene()));
        }
        validateScope(question.getModel(), question.getCategory());

        return questionRepository.save(question);
    }

    @Override
    @Transactional
    public void deleteQuestion(Integer questionId) {
        PresetQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new RuntimeException("预设问题不存在"));
        questionRepository.delete(question);
    }

    @Override
    public List<PresetQuestionResponse> getQuestions(Integer modelId, Integer categoryId) {
        return getQuestions(modelId, categoryId, null);
    }

    @Override
    public List<PresetQuestionResponse> getQuestions(Integer modelId, Integer categoryId, String scene) {
        List<PresetQuestion> questions = questionRepository.findByEnabledTrue();
        Integer targetCategoryId = categoryId;
        if (targetCategoryId == null && modelId != null) {
            var modelOpt = modelRepository.findById(modelId);
            if (modelOpt.isPresent() && modelOpt.get().getCategory() != null) {
                targetCategoryId = modelOpt.get().getCategory().getId();
            }
        }

        final Integer finalModelId = modelId;
        final Integer finalCategoryId = targetCategoryId;
        /*
          场景过滤：
          - 传了 chat / model 时，只留 scene 相同或 scene=all 的条目；
          - 不传（null）时不过滤，管理端与旧接口行为不变。
          这样 AI 问答页可以单独维护一批问题，不必和模型详情页共用。
        */
        final String targetScene = (scene == null || scene.isBlank() || "all".equalsIgnoreCase(scene))
                ? null
                : scene.trim().toLowerCase();

        questions = questions.stream()
            .filter(q -> {
                if (targetScene != null) {
                    String qScene = q.getScene() == null ? "all" : q.getScene().toLowerCase();
                    if (!"all".equals(qScene) && !targetScene.equals(qScene)) {
                        return false;
                    }
                }
                // 全局通用（模型、分类都为空）始终可见
                if (q.getModel() == null && q.getCategory() == null) {
                    return true;
                }
                // 模型专属
                if (q.getModel() != null) {
                    return finalModelId != null && q.getModel().getId().equals(finalModelId);
                }
                // 分类通用
                if (q.getCategory() != null) {
                    return finalCategoryId != null && q.getCategory().getId().equals(finalCategoryId);
                }
                return false;
            })
            .sorted(Comparator
                .comparing((PresetQuestion q) -> q.getSortOrder() == null ? Integer.MAX_VALUE : q.getSortOrder())
                .thenComparing(PresetQuestion::getId))
            .collect(Collectors.toList());

        return questions.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Override
    public Page<PresetQuestionResponse> getQuestionsPage(Pageable pageable) {
        return questionRepository.findByEnabled(1, pageable).map(this::convertToResponse);
    }

    @Override
    @Transactional
    public void incrementClickCount(Integer questionId) {
        questionRepository.incrementClickCount(questionId);
    }

    private PresetQuestionResponse convertToResponse(PresetQuestion question) {
        return PresetQuestionResponse.builder()
            .id(question.getId())
            .categoryId(question.getCategory() != null ? question.getCategory().getId() : null)
            .modelId(question.getModel() != null ? question.getModel().getId() : null)
            .questionText(question.getQuestionText())
            .answerText(question.getAnswerText())
            .sortOrder(question.getSortOrder())
            .clickCount(question.getClickCount())
            .enabled(question.getEnabled() == 1)
            .scene(question.getScene() == null ? "all" : question.getScene())
            .createdAt(question.getCreatedAt())
            .updatedAt(question.getUpdatedAt())
            .build();
    }

    private void validateScope(BuildingModel model, BuildingCategory category) {
        // 允许全局通用（model/category 都为空），由前端"作用范围"显式选择
        if (model != null && category != null && model.getCategory() != null) {
            Integer modelCategoryId = model.getCategory().getId();
            if (!modelCategoryId.equals(category.getId())) {
                throw new RuntimeException("所选模型与分类不匹配");
            }
        }
    }

    /** 场景值只允许 all/chat/model，其余一律回落 all，避免脏数据把列表筛空 */
    private String normalizeScene(String scene) {
        if (scene == null) {
            return "all";
        }
        String s = scene.trim().toLowerCase();
        return ("chat".equals(s) || "model".equals(s)) ? s : "all";
    }
}

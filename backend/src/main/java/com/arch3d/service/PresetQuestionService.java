package com.arch3d.service;

import com.arch3d.dto.PresetQuestionCreateRequest;
import com.arch3d.dto.PresetQuestionUpdateRequest;
import com.arch3d.dto.PresetQuestionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.arch3d.entity.PresetQuestion;

import java.util.List;

public interface PresetQuestionService {
    PresetQuestion createQuestion(PresetQuestionCreateRequest request);
    PresetQuestion updateQuestion(Integer questionId, PresetQuestionUpdateRequest request);
    void deleteQuestion(Integer questionId);
    List<PresetQuestionResponse> getQuestions(Integer modelId, Integer categoryId);

    /**
     * 按展示场景取预设问题。
     *
     * @param scene chat = AI 问答页；model = 模型详情页悬浮问答；null = 不限场景
     */
    List<PresetQuestionResponse> getQuestions(Integer modelId, Integer categoryId, String scene);

    Page<PresetQuestionResponse> getQuestionsPage(Pageable pageable);
    void incrementClickCount(Integer questionId);
}

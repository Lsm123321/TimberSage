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
    Page<PresetQuestionResponse> getQuestionsPage(Pageable pageable);
    void incrementClickCount(Integer questionId);
}

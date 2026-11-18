package com.arch3d.controller;

import com.arch3d.dto.PresetQuestionCreateRequest;
import com.arch3d.dto.PresetQuestionUpdateRequest;
import com.arch3d.dto.PresetQuestionResponse;
import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.PageDTO;
import com.arch3d.service.PresetQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "预设问题", description = "预设问题管理接口")
public class PresetQuestionController {

    private final PresetQuestionService questionService;

    // 管理员CRUD接口
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/admin/preset-questions")
    @Operation(summary = "创建预设问题", description = "管理员创建预设问题")
    public ResponseEntity<PresetQuestionResponse> createQuestion(
            @Valid @RequestBody PresetQuestionCreateRequest request) {

        var question = questionService.createQuestion(request);

        PresetQuestionResponse response = PresetQuestionResponse.builder()
            .id(question.getId())
            .categoryId(question.getCategory() != null ? question.getCategory().getId() : null)
            .modelId(question.getModel() != null ? question.getModel().getId() : null)
            .questionText(question.getQuestionText())
            .answerText(question.getAnswerText())
            .sortOrder(question.getSortOrder())
            .clickCount(question.getClickCount())
            .enabled(question.getEnabled() == 1)
            .createdAt(question.getCreatedAt())
            .updatedAt(question.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/admin/preset-questions/{id}")
    @Operation(summary = "更新预设问题", description = "管理员更新预设问题")
    public ResponseEntity<PresetQuestionResponse> updateQuestion(
            @PathVariable Integer id,
            @Valid @RequestBody PresetQuestionUpdateRequest request) {

        var question = questionService.updateQuestion(id, request);

        PresetQuestionResponse response = PresetQuestionResponse.builder()
            .id(question.getId())
            .categoryId(question.getCategory() != null ? question.getCategory().getId() : null)
            .modelId(question.getModel() != null ? question.getModel().getId() : null)
            .questionText(question.getQuestionText())
            .answerText(question.getAnswerText())
            .sortOrder(question.getSortOrder())
            .clickCount(question.getClickCount())
            .enabled(question.getEnabled() == 1)
            .createdAt(question.getCreatedAt())
            .updatedAt(question.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/admin/preset-questions/{id}")
    @Operation(summary = "删除预设问题", description = "管理员删除预设问题")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Integer id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin/preset-questions")
    @Operation(summary = "获取所有预设问题", description = "管理员获取所有预设问题")
    public ResponseEntity<ApiResponse<PageDTO<PresetQuestionResponse>>> getAllQuestions(
            @RequestParam(required = false) Integer modelId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        // 管理端列表统一按分页结构返回（records/total/page/pageSize）。
        // 带了 modelId/categoryId 筛选时是全量查出来再在内存里切页
        if (modelId != null || categoryId != null) {
            List<PresetQuestionResponse> all = questionService.getQuestions(modelId, categoryId);
            int safePage = Math.max(page, 1);
            int safeSize = Math.max(pageSize, 1);
            int from = (safePage - 1) * safeSize;
            int to = Math.min(from + safeSize, all.size());
            List<PresetQuestionResponse> records = from >= all.size() ? List.of() : all.subList(from, to);
            PageDTO<PresetQuestionResponse> dto = PageDTO.<PresetQuestionResponse>builder()
                    .total((long) all.size())
                    .page(safePage)
                    .pageSize(safeSize)
                    .records(records)
                    .build();
            return ResponseEntity.ok(ApiResponse.success(dto));
        }

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1), Sort.by("sortOrder").ascending().and(Sort.by("id").descending()));
        Page<PresetQuestionResponse> p = questionService.getQuestionsPage(pageable);
        PageDTO<PresetQuestionResponse> dto = PageDTO.<PresetQuestionResponse>builder()
                .total(p.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(p.getContent())
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // 普通用户获取预设问题
    @GetMapping("/preset-questions")
    @Operation(summary = "获取预设问题",
            description = "获取指定模型的预设问题；scene 区分场景：chat=AI问答页，model=模型详情页悬浮问答，不传=全部")
    public ResponseEntity<List<PresetQuestionResponse>> getQuestions(
            @RequestParam(required = false) Integer modelId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String scene) {

        List<PresetQuestionResponse> questions = questionService.getQuestions(modelId, categoryId, scene);
        return ResponseEntity.ok(questions);
    }

    @PostMapping("/preset-questions/{id}/click")
    @Operation(summary = "记录点击", description = "记录预设问题点击次数")
    public ResponseEntity<Void> recordClick(@PathVariable Integer id) {
        questionService.incrementClickCount(id);
        return ResponseEntity.ok().build();
    }
}

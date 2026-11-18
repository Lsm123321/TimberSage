package com.arch3d.controller;

import com.arch3d.dto.*;
import com.arch3d.entity.KbEntry;
import com.arch3d.repository.KbEntryRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.service.KnowledgeService;
import com.arch3d.service.KnowledgeGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库管理接口。
 *
 * 全部沿用 AdminController 的约定：类级 /api/admin，方法级 @PreAuthorize，
 * 统一返回 ApiResponse 包装（不像 preset_question 那样裸 DTO / ApiResponse 混用）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "AI 问答知识库的增删改查与审核")
public class KnowledgeController {

    private final KbEntryRepository kbEntryRepository;
    private final BuildingModelRepository modelRepository;
    private final KnowledgeService knowledgeService;
    private final KnowledgeGraphService knowledgeGraphService;

    private static final int STATUS_CANDIDATE = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_ARCHIVED = 3;

    // ---------------------------------------------------------------- 查询

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "分页查询知识条目",
            description = "支持按模型、分类、类型、状态、关键词筛选。查全部状态，不只是已通过的")
    public ResponseEntity<ApiResponse<PageDTO<KbEntryDTO>>> list(
            @RequestParam(required = false) Integer modelId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        Sort.Direction dir = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // sortBy 只允许这几个字段，避免前端传进来的值直接进 order by
        Set<String> allowed = Set.of("id", "hitCount", "updatedAt", "createdAt", "status");
        String orderField = allowed.contains(sortBy) ? sortBy : "id";

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), Math.max(pageSize, 1),
                Sort.by(dir, orderField));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String qt = (questionType == null || questionType.isBlank()) ? null : questionType;

        Page<KbEntry> result = kbEntryRepository.search(modelId, categoryId, qt, status, kw, pageable);

        PageDTO<KbEntryDTO> dto = PageDTO.<KbEntryDTO>builder()
                .total(result.getTotalElements())
                .page(page)
                .pageSize(pageSize)
                .records(result.getContent().stream().map(this::toDTO).toList())
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "查询知识条目详情")
    public ResponseEntity<ApiResponse<KbEntryDTO>> detail(@PathVariable Integer id) {
        return kbEntryRepository.findById(id)
                .map(e -> ResponseEntity.ok(ApiResponse.success(toDTO(e))))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.error(404, "知识条目不存在")));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "知识库统计", description = "各状态数量、总命中、节省 token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = kbEntryRepository.count();
        long approved = kbEntryRepository.countByStatus(STATUS_APPROVED);
        long candidate = kbEntryRepository.countByStatus(STATUS_CANDIDATE);
        long rejected = kbEntryRepository.countByStatus(STATUS_REJECTED);
        long archived = kbEntryRepository.countByStatus(STATUS_ARCHIVED);

        long totalHits = 0;
        long totalSaved = 0;
        for (KbEntry e : kbEntryRepository.findAll()) {
            totalHits += e.getHitCount() == null ? 0 : e.getHitCount();
            totalSaved += e.getTokensSaved() == null ? 0 : e.getTokensSaved();
        }

        stats.put("total", total);
        stats.put("approved", approved);
        stats.put("candidate", candidate);
        stats.put("rejected", rejected);
        stats.put("archived", archived);
        stats.put("totalHits", totalHits);
        stats.put("tokensSaved", totalSaved);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // ---------------------------------------------------------------- 增删改

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "新增知识条目", description = "默认落成候选状态，审核通过后才参与检索")
    public ResponseEntity<ApiResponse<KbEntryDTO>> create(@Valid @RequestBody KbEntryRequest req,
                                                          @RequestParam(required = false) Integer reviewerId) {
        var model = modelRepository.findById(req.getModelId())
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + req.getModelId()));

        KbEntry entry = KbEntry.builder()
                .model(model)
                .categoryId(model.getCategory() == null ? null : model.getCategory().getId())
                .question(req.getQuestion().trim())
                .normalizedQuestion(knowledgeService.normalize(req.getQuestion()))
                .answer(req.getAnswer().trim())
                .answerSource(req.getAnswerSource() == null ? "MANUAL" : req.getAnswerSource())
                .questionType(req.getQuestionType())
                .status(req.getStatus() == null ? STATUS_CANDIDATE : req.getStatus())
                .reviewedBy(req.getStatus() != null && req.getStatus() == STATUS_APPROVED ? reviewerId : null)
                .reviewedAt(req.getStatus() != null && req.getStatus() == STATUS_APPROVED
                        ? LocalDateTime.now() : null)
                .build();

        KbEntry saved = kbEntryRepository.save(entry);
        return ResponseEntity.ok(ApiResponse.success("新增成功", toDTO(saved)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "编辑知识条目",
            description = "会重新计算归一化问题并让旧缓存失效，避免改了答案还返回旧内容")
    @Transactional
    public ResponseEntity<ApiResponse<KbEntryDTO>> update(@PathVariable Integer id,
                                                          @Valid @RequestBody KbEntryRequest req) {
        KbEntry entry = kbEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识条目不存在: " + id));

        // 改之前先记住旧值，用来清缓存
        Integer oldModelId = entry.getModel() == null ? null : entry.getModel().getId();
        String oldNorm = entry.getNormalizedQuestion();

        var model = modelRepository.findById(req.getModelId())
                .orElseThrow(() -> new IllegalArgumentException("模型不存在: " + req.getModelId()));

        entry.setModel(model);
        entry.setCategoryId(model.getCategory() == null ? null : model.getCategory().getId());
        entry.setQuestion(req.getQuestion().trim());
        entry.setNormalizedQuestion(knowledgeService.normalize(req.getQuestion()));
        entry.setAnswer(req.getAnswer().trim());
        if (req.getQuestionType() != null) {
            entry.setQuestionType(req.getQuestionType());
        }
        if (req.getAnswerSource() != null) {
            entry.setAnswerSource(req.getAnswerSource());
        }
        if (req.getStatus() != null) {
            entry.setStatus(req.getStatus());
            entry.setRejectReason(req.getStatus() == STATUS_REJECTED ? req.getRejectReason() : null);
        }
        entry.setKnowledgeVersion((entry.getKnowledgeVersion() == null ? 1 : entry.getKnowledgeVersion()) + 1);

        KbEntry saved = kbEntryRepository.save(entry);

        // 模型可能换了，新旧两个 key 都要清
        knowledgeService.evictCache(oldModelId, oldNorm);
        knowledgeService.evictCache(saved.getModel() == null ? null : saved.getModel().getId(),
                saved.getNormalizedQuestion());
        return ResponseEntity.ok(ApiResponse.success("保存成功", toDTO(saved)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除知识条目", description = "物理删除；建议运营上用归档代替")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        KbEntry entry = kbEntryRepository.findById(id).orElse(null);
        if (entry == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "知识条目不存在"));
        }
        knowledgeService.evictCache(entry.getModel() == null ? null : entry.getModel().getId(),
                entry.getNormalizedQuestion());
        kbEntryRepository.delete(entry);
        return ResponseEntity.ok(ApiResponse.success("删除成功", null));
    }

    // ---------------------------------------------------------------- 审核

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "审核单条知识", description = "approve / reject / archive / restore")
    @Transactional
    public ResponseEntity<ApiResponse<KbEntryDTO>> changeStatus(@PathVariable Integer id,
                                                                @RequestParam String action,
                                                                @RequestParam(required = false) Integer reviewerId,
                                                                @RequestParam(required = false) String reason) {
        KbEntry entry = kbEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("知识条目不存在: " + id));

        int target = switch (action) {
            case "approve" -> STATUS_APPROVED;
            case "reject" -> STATUS_REJECTED;
            case "archive" -> STATUS_ARCHIVED;
            case "restore" -> STATUS_CANDIDATE;
            default -> throw new IllegalArgumentException("不支持的操作: " + action);
        };

        entry.setStatus(target);
        if (target == STATUS_APPROVED) {
            entry.setReviewedBy(reviewerId);
            entry.setReviewedAt(LocalDateTime.now());
            entry.setRejectReason(null);
        } else if (target == STATUS_REJECTED) {
            entry.setRejectReason(reason);
            entry.setReviewedAt(LocalDateTime.now());
        }
        entry.setKnowledgeVersion((entry.getKnowledgeVersion() == null ? 1 : entry.getKnowledgeVersion()) + 1);
        KbEntry saved = kbEntryRepository.save(entry);

        knowledgeService.evictCache(saved.getModel() == null ? null : saved.getModel().getId(),
                saved.getNormalizedQuestion());
        return ResponseEntity.ok(ApiResponse.success("操作成功", toDTO(saved)));
    }

    @PutMapping("/batch-status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "批量审核")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchStatus(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Integer reviewerId) {

        Object idsObj = body.get("ids");
        if (!(idsObj instanceof List<?> rawIds) || rawIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "ids 不能为空"));
        }
        String action = Objects.toString(body.get("action"), "approve");
        String reason = Objects.toString(body.get("reason"), null);

        int target = switch (action) {
            case "approve" -> STATUS_APPROVED;
            case "reject" -> STATUS_REJECTED;
            case "archive" -> STATUS_ARCHIVED;
            default -> throw new IllegalArgumentException("不支持的操作: " + action);
        };

        int updated = 0;
        for (Object o : rawIds) {
            Integer id;
            try {
                id = Integer.valueOf(String.valueOf(o));
            } catch (NumberFormatException e) {
                continue;
            }
            KbEntry entry = kbEntryRepository.findById(id).orElse(null);
            if (entry == null) {
                continue;
            }
            entry.setStatus(target);
            if (target == STATUS_APPROVED) {
                entry.setReviewedBy(reviewerId);
                entry.setReviewedAt(LocalDateTime.now());
                entry.setRejectReason(null);
            } else if (target == STATUS_REJECTED) {
                entry.setRejectReason(reason);
                entry.setReviewedAt(LocalDateTime.now());
            }
            entry.setKnowledgeVersion((entry.getKnowledgeVersion() == null ? 1 : entry.getKnowledgeVersion()) + 1);
            kbEntryRepository.save(entry);
            knowledgeService.evictCache(entry.getModel() == null ? null : entry.getModel().getId(),
                    entry.getNormalizedQuestion());
            updated++;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updated", updated);
        res.put("action", action);
        return ResponseEntity.ok(ApiResponse.success("批量操作完成", res));
    }

    // ---------------------------------------------------------------- 图谱

    @GetMapping("/graph")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "知识图谱数据",
            description = "分类→模型两层，withTypes=true 时把每模型的知识类型也展开")
    public ResponseEntity<ApiResponse<KnowledgeGraphDTO>> graph(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "false") boolean withTypes) {
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeGraphService.buildGraph(categoryId, withTypes)));
    }

    @GetMapping("/graph/expand/{modelId}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "展开单个模型的知识类型节点",
            description = "点击模型节点时按需加载，避免首屏一次拉 468 个节点")
    public ResponseEntity<ApiResponse<KnowledgeGraphDTO>> expand(@PathVariable Integer modelId) {
        return ResponseEntity.ok(ApiResponse.success(
                knowledgeGraphService.expandModel(modelId)));
    }

    // ---------------------------------------------------------------- 工具

    private KbEntryDTO toDTO(KbEntry e) {
        var model = e.getModel();
        return KbEntryDTO.builder()
                .id(e.getId())
                .modelId(model == null ? null : model.getId())
                .modelName(model == null ? null : model.getModelName())
                .categoryId(e.getCategoryId())
                .categoryName(model != null && model.getCategory() != null
                        ? model.getCategory().getCategoryName() : null)
                .question(e.getQuestion())
                .normalizedQuestion(e.getNormalizedQuestion())
                .answer(e.getAnswer())
                .answerSource(e.getAnswerSource())
                .questionType(e.getQuestionType())
                .status(e.getStatus())
                .hitCount(e.getHitCount())
                .helpfulCount(e.getHelpfulCount())
                .tokensSaved(e.getTokensSaved())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .rejectReason(e.getRejectReason())
                .knowledgeVersion(e.getKnowledgeVersion())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}

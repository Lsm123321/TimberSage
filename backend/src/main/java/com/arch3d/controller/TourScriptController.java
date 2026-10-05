package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.TourScriptResponse;
import com.arch3d.entity.BuildingModel;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.service.TourScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 语音导览：分章脚本生成。
 * 前端把当前模型的构件名清单（mesh 命名）随请求带上，脚本里的 partName 才能落到真实 mesh 上。
 */
@Slf4j
@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "AI语音导览", description = "生成分章导览脚本（旁白+镜头+构件高亮）")
public class TourScriptController {

    private final TourScriptService tourScriptService;
    private final BuildingModelRepository buildingModelRepository;

    /**
     * 请求体：构件名清单（可选）。构件名来自前端 ThreeScene 的 getPartsList()。
     */
    public record TourScriptRequest(List<String> partNames) {
    }

    @PostMapping("/{id}/tour-script")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取 AI 语音导览脚本", description = "基于模型信息与构件清单生成分章导览脚本，Redis 缓存 24h")
    public ResponseEntity<ApiResponse<TourScriptResponse>> getTourScript(
            @PathVariable Integer id,
            @RequestBody(required = false) TourScriptRequest request) {

        BuildingModel model = buildingModelRepository.findById(id)
                .orElse(null);
        if (model == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "模型不存在"));
        }
        if (model.getStatus() == null || model.getStatus() != 1) {
            return ResponseEntity.ok(ApiResponse.error(403, "模型未发布，暂不支持导览"));
        }

        // 构件名去重截断：mesh 可能上千个，挑前几十个有意义的名字给 LLM 就够了
        List<String> partNames = request != null && request.partNames() != null
                ? request.partNames().stream()
                    .filter(n -> n != null && !n.isBlank() && !n.matches("mesh_\\d+"))
                    .distinct()
                    .limit(60)
                    .toList()
                : List.of();

        TourScriptResponse script = tourScriptService.getOrGenerateScript(
                model.getModelName(),
                model.getDynasty(),
                model.getLocation(),
                model.getDescription(),
                partNames);

        return ResponseEntity.ok(ApiResponse.success(script));
    }
}

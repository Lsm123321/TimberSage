package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.entity.BuildingModel;
import com.arch3d.service.MapService;
import com.arch3d.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
@Tag(name = "地图导览", description = "地图点位与AI导览接口")
public class MapController {
    private final MapService mapService;
    private final MinioService minioService;

    @Value("${app.map.compliance.source:国家地理信息公共服务平台（https://www.tianditu.gov.cn/）}")
    private String mapSource;

    @Value("${app.map.compliance.review-no:待配置审图号}")
    private String reviewNo;

    @Value("${app.map.compliance.review-year:2026}")
    private String reviewYear;

    @GetMapping("/points")
    @Operation(summary = "获取地图点位", description = "按关键词过滤已发布模型并返回地图点位")
    public ApiResponse<Map<String, Object>> getMapPoints(@RequestParam(value = "keyword", required = false) String keyword) {
        Map<String, Object> payload = new LinkedHashMap<>(mapService.getMapOverview(keyword));
        payload.put("compliance", Map.of(
            "source", mapSource,
            "reviewNo", reviewNo,
            "reviewYear", reviewYear,
            "configured", !reviewNo.contains("待配置")
        ));
        return ApiResponse.success(payload);
    }

    @GetMapping("/regions/{regionCode}/models")
    @Operation(summary = "获取区域模型列表", description = "点击地图点位后获取该区域的模型卡片")
    public ApiResponse<List<Map<String, Object>>> getRegionModels(@PathVariable String regionCode) {
        List<Map<String, Object>> rows = mapService.getRegionModels(regionCode).stream()
            .map(model -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", model.getId());
                row.put("modelName", model.getModelName());
                row.put("dynasty", model.getDynasty());
                row.put("location", model.getLocation());
                row.put("description", model.getDescription());
                row.put("thumbnailPath", resolveThumbnailUrl(model.getThumbnailPath()));
                return row;
            })
            .collect(Collectors.toList());
        return ApiResponse.success(rows);
    }

    @PostMapping("/insight")
    @Operation(summary = "生成模型讲解文案", description = "根据模型信息实时生成建筑成就与历史意义")
    public ApiResponse<Map<String, Object>> generateInsight(@RequestBody Map<String, Object> body) {
        Integer modelId = body.get("modelId") instanceof Number ? ((Number) body.get("modelId")).intValue() : null;
        String modelName = body.getOrDefault("modelName", "").toString();
        String dynasty = body.getOrDefault("dynasty", "").toString();
        String location = body.getOrDefault("location", "").toString();
        String description = body.getOrDefault("description", "").toString();
        return ApiResponse.success(mapService.generateModelInsight(modelId, modelName, dynasty, location, description));
    }

    private String resolveThumbnailUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        if (objectName.startsWith("http://") || objectName.startsWith("https://")) {
            return objectName;
        }
        try {
            return minioService.getThumbnailUrl(objectName);
        } catch (Exception ignored) {
            return objectName;
        }
    }
}


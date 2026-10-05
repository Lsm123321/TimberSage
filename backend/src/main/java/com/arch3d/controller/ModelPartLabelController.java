package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.dto.PartBlurbRequest;
import com.arch3d.dto.PartBlurbResponse;
import com.arch3d.dto.PartLabelRequest;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.ChatService;
import com.arch3d.service.ModelPartLabelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
@Tag(name = "模型构件", description = "3D 模型部件 AI 中文命名")
public class ModelPartLabelController {

    private final ModelPartLabelService modelPartLabelService;
    private final ChatService chatService;

    @PostMapping("/{modelId}/part-labels")
    @Operation(summary = "部件中文命名", description = "根据 glTF 网格/节点技术名批量生成中文构件名")
    public ResponseEntity<ApiResponse<Map<String, String>>> labelParts(
            @PathVariable Integer modelId,
            @Valid @RequestBody PartLabelRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "请先登录"));
        }
        if (!chatService.checkRateLimit(userPrincipal.getId())) {
            return ResponseEntity.status(429).body(ApiResponse.error(429, "请求过于频繁，请稍后再试"));
        }

        try {
            Map<String, String> labels = modelPartLabelService.labelParts(modelId, request);
            return ResponseEntity.ok(ApiResponse.success(labels));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{modelId}/part-blurb")
    @Operation(summary = "单构件介绍", description = "结合模型信息与构件名称生成简短中文介绍")
    public ResponseEntity<ApiResponse<PartBlurbResponse>> partBlurb(
            @PathVariable Integer modelId,
            @Valid @RequestBody PartBlurbRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (userPrincipal == null) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "请先登录"));
        }
        if (!chatService.checkRateLimit(userPrincipal.getId())) {
            return ResponseEntity.status(429).body(ApiResponse.error(429, "请求过于频繁，请稍后再试"));
        }

        try {
            PartBlurbResponse body = modelPartLabelService.describePart(
                modelId,
                request.getTechnicalName(),
                request.getAiLabel()
            );
            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}

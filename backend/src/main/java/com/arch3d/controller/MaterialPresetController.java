package com.arch3d.controller;

import com.arch3d.dto.MaterialPresetCreateRequest;
import com.arch3d.dto.MaterialPresetResponse;
import com.arch3d.security.UserPrincipal;
import com.arch3d.service.MaterialPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/material-presets")
@RequiredArgsConstructor
@Tag(name = "材质方案", description = "用户材质方案管理接口")
public class MaterialPresetController {

    private final MaterialPresetService presetService;

    @PostMapping
    @Operation(summary = "保存材质方案", description = "保存用户对模型的材质方案")
    public ResponseEntity<MaterialPresetResponse> createPreset(
            @Valid @RequestBody MaterialPresetCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        var preset = presetService.createPreset(request, userPrincipal.getId());

        MaterialPresetResponse response = MaterialPresetResponse.builder()
            .id(preset.getId())
            .presetName(preset.getPresetName())
            .modelId(preset.getModel().getId())
            .materialParams(preset.getMaterialParams())
            .userId(preset.getUser().getId())
            .createdAt(preset.getCreatedAt())
            .updatedAt(preset.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "获取材质方案列表", description = "获取当前用户对指定模型的材质方案列表")
    public ResponseEntity<List<MaterialPresetResponse>> getPresets(
            @RequestParam Integer modelId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        List<MaterialPresetResponse> presets = presetService.getPresetsByModel(modelId, userPrincipal.getId());
        return ResponseEntity.ok(presets);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除材质方案", description = "删除指定的材质方案")
    public ResponseEntity<Void> deletePreset(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        presetService.deletePreset(id, userPrincipal.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/apply")
    @Operation(summary = "获取材质参数", description = "获取材质方案的参数JSON")
    public ResponseEntity<String> getMaterialParams(
            @PathVariable Integer id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        String materialParams = presetService.getMaterialParams(id, userPrincipal.getId());
        return ResponseEntity.ok(materialParams);
    }
}

package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.service.DownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/models")
@RequiredArgsConstructor
@Tag(name = "公开模型", description = "匿名可访问的模型预览地址")
public class PublicModelController {

    private final DownloadService downloadService;

    @GetMapping("/{id}/file-url")
    @Operation(summary = "匿名预览模型文件URL", description = "仅已发布且下载权限为「允许所有人」时返回 MinIO 预签名地址")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPublicFileUrl(@PathVariable Integer id) {
        try {
            String url = downloadService.getPublicViewFileUrl(id);
            return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, e.getMessage()));
        }
    }
}

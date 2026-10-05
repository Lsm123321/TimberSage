package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.entity.CustomVoice;
import com.arch3d.repository.CustomVoiceRepository;
import com.arch3d.service.VoiceCloneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 管理端：克隆音色管理（上传样本 → 克隆 → 试听 → 启停/删除）。
 * 仅 admin 角色可操作；启用的音色会出现在前端导览播放条的音色下拉里。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/voices")
@RequiredArgsConstructor
@Tag(name = "声音克隆管理", description = "管理员上传音色样本并克隆自定义朗读音色")
public class AdminVoiceController {

    private final VoiceCloneService voiceCloneService;
    private final CustomVoiceRepository customVoiceRepository;

    /** 克隆请求体 */
    public record CloneRequest(String voiceName, String fileId, String previewText) {
    }

    /** 上传音色样本（3~30 秒清晰人声，wav/mp3/m4a，≤10MB），返回 file_id */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "上传音色样本")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String fileId = voiceCloneService.uploadSample(file);
            return ResponseEntity.ok(ApiResponse.success(Map.of("fileId", fileId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("音色样本上传异常", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    /** 提交克隆：file_id + 展示名 + 试听文本 → 落库并返回音色记录 */
    @PostMapping("/clone")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "提交声音克隆")
    public ResponseEntity<ApiResponse<CustomVoice>> clone(
            @RequestBody CloneRequest request,
            Authentication authentication) {
        Integer userId = extractUserId(authentication);
        try {
            if (request.voiceName() == null || request.voiceName().isBlank()) {
                return ResponseEntity.ok(ApiResponse.error(400, "音色名称不能为空"));
            }
            if (request.fileId() == null || request.fileId().isBlank()) {
                return ResponseEntity.ok(ApiResponse.error(400, "请先上传音色样本"));
            }
            CustomVoice voice = voiceCloneService.clone(request.voiceName(), request.fileId(), request.previewText(), userId);
            return ResponseEntity.ok(ApiResponse.success(voice));
        } catch (Exception e) {
            log.error("声音克隆异常", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    /** 全部音色（管理视图，含停用的） */
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "音色列表")
    public ResponseEntity<ApiResponse<List<CustomVoice>>> list() {
        return ResponseEntity.ok(ApiResponse.success(customVoiceRepository.findAllByOrderByCreatedAtDesc()));
    }

    /** 启用/停用 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "启用/停用音色")
    public ResponseEntity<ApiResponse<CustomVoice>> updateStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        return customVoiceRepository.findById(id)
            .<ResponseEntity<ApiResponse<CustomVoice>>>map(voice -> {
                String status = "enabled".equals(body.get("status")) ? "enabled" : "disabled";
                voice.setStatus(status);
                return ResponseEntity.ok(ApiResponse.success(customVoiceRepository.save(voice)));
            })
            .orElse(ResponseEntity.ok(ApiResponse.error(404, "音色不存在")));
    }

    /** 删除只删本地记录；智谱侧的音色不占额度，不用调它的删除接口 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除音色")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        customVoiceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 试听：用该音色合成一段语音返回 wav */
    @PostMapping("/{id}/preview")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "试听克隆音色")
    public ResponseEntity<?> preview(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body) {
        return customVoiceRepository.findById(id)
            .<ResponseEntity<?>>map(voice -> {
                try {
                    byte[] audio = voiceCloneService.preview(voice, body != null ? body.get("text") : null);
                    return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/wav"))
                        .body(audio);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error(529, e.getMessage()));
                }
            })
            .orElse(ResponseEntity.ok(ApiResponse.error(404, "音色不存在")));
    }

    /** 从 Authentication 的 principal（UserPrincipal）取用户 ID */
    private Integer extractUserId(Authentication authentication) {
        try {
            Object principal = authentication.getPrincipal();
            java.lang.reflect.Method m = principal.getClass().getMethod("getId");
            return (Integer) m.invoke(principal);
        } catch (Exception e) {
            return null;
        }
    }
}

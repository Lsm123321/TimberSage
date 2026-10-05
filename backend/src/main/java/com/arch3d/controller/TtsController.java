package com.arch3d.controller;

import com.arch3d.dto.ApiResponse;
import com.arch3d.service.TtsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TTS 语音合成代理：前端传文本+音色，转发智谱 CogTTS 并返回音频流。
 * 智谱不可用时返回 503 + 固定 code，前端据此降级到浏览器内置 speechSynthesis。
 */
@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@Tag(name = "TTS语音合成", description = "文本转语音（智谱CogTTS代理）")
public class TtsController {

    private final TtsService ttsService;
    private final com.arch3d.repository.CustomVoiceRepository customVoiceRepository;

    public record TtsRequest(String text, String voice) {
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "合成语音", description = "文本+音色 -> wav；智谱不可用时返回 503/code=529 供前端降级")
    public ResponseEntity<?> synthesize(@RequestBody TtsRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error(400, "文本不能为空"));
        }
        // 克隆音色用 custom:<custom_voice.id> 标识，查库换回真正的智谱 voice ID
        String voice = request.voice();
        if (voice != null && voice.startsWith("custom:")) {
            Integer voicePk = null;
            try {
                voicePk = Integer.parseInt(voice.substring(7));
            } catch (NumberFormatException ignored) {
            }
            final Integer pk = voicePk;
            java.util.Optional<com.arch3d.entity.CustomVoice> cv = customVoiceRepository.findById(pk == null ? -1 : pk);
            if (cv.isEmpty() || !"enabled".equals(cv.get().getStatus())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(529, "音色不可用，已切换本地朗读"));
            }
            voice = cv.get().getVoiceId();
        }
        try {
            byte[] audio = ttsService.synthesize(request.text(), voice);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(audio);
        } catch (TtsService.TtsUnavailableException e) {
            // code=529 表示"TTS 不可用，前端降级到本地朗读"
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(529, "语音服务暂不可用，已切换本地朗读"));
        }
    }

    /** 音色清单：预设音色 + 启用的克隆音色（前端导览下拉直接用） */
    @GetMapping("/voices")
    @Operation(summary = "获取可用音色列表")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> voices() {
        List<Map<String, String>> voices = new java.util.ArrayList<>(List.of(
                Map.of("id", "tongtong", "name", "桐桐 · 温柔女声", "desc", "智谱 CogTTS", "source", "preset"),
                Map.of("id", "xiaochen", "name", "小陈 · 阳光男声", "desc", "智谱 CogTTS", "source", "preset"),
                Map.of("id", "langlang", "name", "朗朗 · 沉稳播音", "desc", "智谱 CogTTS", "source", "preset"),
                Map.of("id", "local-auto", "name", "本地默认音色", "desc", "浏览器内置（离线可用）", "source", "local")
        ));
        // 克隆音色也并进来：id 用 custom:<pk>，合成时再查库换回智谱 voice ID
        for (com.arch3d.entity.CustomVoice cv : customVoiceRepository.findByStatusOrderByCreatedAtDesc("enabled")) {
            voices.add(Map.of(
                "id", "custom:" + cv.getId(),
                "name", cv.getVoiceName() + " · 克隆",
                "desc", "声音克隆",
                "source", "cloned"
            ));
        }
        return ResponseEntity.ok(ApiResponse.success(voices));
    }
}

package com.arch3d.service;

import com.arch3d.entity.CustomVoice;
import com.arch3d.repository.CustomVoiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 声音克隆服务（智谱 GLM-TTS-Clone）。
 *
 * 流程：
 * 1. uploadSample：管理员上传 3~30 秒人声样本（wav/mp3，≤10MB）→ /v4/files 拿 file_id
 * 2. clone：/v4/voice/clone（model=glm-tts-clone + file_id + voice_name + input 试听文本）→ voice ID
 * 3. 合成：复用 /v4/audio/speech，voice 参数传克隆的 voice ID（TtsService 已支持）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceCloneService {

    private final CustomVoiceRepository customVoiceRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.zhipu.api-key:}")
    private String apiKey;

    @Value("${spring.zhipu.clone-upload-url:https://open.bigmodel.cn/api/paas/v4/files}")
    private String uploadUrl;

    @Value("${spring.zhipu.clone-url:https://open.bigmodel.cn/api/paas/v4/voice/clone}")
    private String cloneUrl;

    @Value("${spring.zhipu.clone-model:glm-tts-clone}")
    private String cloneModel;

    /** 试听合成地址（与 TTS 同端点，但克隆音色需用 glm-tts 模型） */
    @Value("${spring.zhipu.tts-url:https://open.bigmodel.cn/api/paas/v4/audio/speech}")
    private String ttsUrl;

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build();

    /** 样本格式白名单 */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
        "audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3", "audio/x-m4a", "audio/mp4");

    /** 上传音色样本，返回智谱 file_id */
    public String uploadSample(MultipartFile file) {
        String key = requireKey();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("仅支持 wav/mp3/m4a 音频格式");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("样本不能超过 10MB");
        }

        try {
            RequestBody fileBody = RequestBody.create(file.getBytes(), MediaType.parse(contentType));
            MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("purpose", "voice-clone-input")
                .addFormDataPart("file", "sample.wav", fileBody)
                .build();

            Request request = new Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer " + key)
                .post(multipart)
                .build();

            try (Response response = client.newCall(request).execute()) {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.warn("音色样本上传失败: HTTP {} {}", response.code(), bodyStr.substring(0, Math.min(bodyStr.length(), 200)));
                    throw new IllegalStateException("样本上传失败（智谱侧），请稍后重试");
                }
                JsonNode node = objectMapper.readTree(bodyStr);
                String fileId = node.path("id").asText(null);
                if (fileId == null) {
                    throw new IllegalStateException("样本上传返回异常");
                }
                return fileId;
            }
        } catch (IOException e) {
            log.error("音色样本上传 IO 异常", e);
            throw new IllegalStateException("样本上传失败，请检查网络后重试");
        }
    }

    /**
     * 提交声音克隆并落库。
     *
     * @param voiceName  展示名
     * @param fileId     上传样本返回的 file_id
     * @param previewText 试听文本（克隆时会生成一段该音色的试听音频）
     * @param userId     操作管理员 ID
     */
    public CustomVoice clone(String voiceName, String fileId, String previewText, Integer userId) {
        String key = requireKey();
        try {
            String json = objectMapper.writeValueAsString(java.util.Map.of(
                "model", cloneModel,
                "file_id", fileId,
                "voice_name", "timbersage_" + UUID.randomUUID().toString().substring(0, 8),
                "input", previewText == null || previewText.isBlank() ? "大家好，欢迎来到智筑云览。" : previewText
            ));

            Request request = new Request.Builder()
                .url(cloneUrl)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

            try (Response response = client.newCall(request).execute()) {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.warn("声音克隆失败: HTTP {} {}", response.code(), bodyStr.substring(0, Math.min(bodyStr.length(), 200)));
                    throw new IllegalStateException("声音克隆失败（智谱侧）：HTTP " + response.code());
                }
                JsonNode node = objectMapper.readTree(bodyStr);
                String voiceId = node.path("voice").asText(null);
                if (voiceId == null || voiceId.isBlank()) {
                    throw new IllegalStateException("克隆返回缺少 voice ID");
                }

                CustomVoice voice = CustomVoice.builder()
                    .voiceName(voiceName)
                    .voiceId(voiceId)
                    .cloneModel(cloneModel)
                    .previewText(previewText)
                    .status("enabled")
                    .createdBy(userId)
                    .build();
                return customVoiceRepository.save(voice);
            }
        } catch (IOException e) {
            log.error("声音克隆 IO 异常", e);
            throw new IllegalStateException("声音克隆请求失败，请稍后重试");
        }
    }

    /** 用克隆音色合成试听音频（返回 wav 二进制，前端 Audio 播放） */
    public byte[] preview(CustomVoice voice, String text) {
        String key = requireKey();
        try {
            String json = objectMapper.writeValueAsString(java.util.Map.of(
                "model", "glm-tts",
                "input", text == null || text.isBlank() ? (voice.getPreviewText() == null ? "大家好，欢迎来到智筑云览。" : voice.getPreviewText()) : text,
                "voice", voice.getVoiceId(),
                "response_format", "wav"
            ));

            Request request = new Request.Builder()
                .url(ttsUrl)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.warn("克隆音色试听失败: HTTP {} {}", response.code(), err.substring(0, Math.min(err.length(), 200)));
                    throw new IllegalStateException("试听合成失败（智谱侧）：HTTP " + response.code());
                }
                return response.body() != null ? response.body().bytes() : new byte[0];
            }
        } catch (IOException e) {
            log.error("克隆音色试听 IO 异常", e);
            throw new IllegalStateException("试听请求失败，请稍后重试");
        }
    }

    private String requireKey() {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("智谱 API Key 未配置，无法使用声音克隆（请在环境变量 ZHIPU_API_KEY 中配置）");
        }
        return key;
    }
}

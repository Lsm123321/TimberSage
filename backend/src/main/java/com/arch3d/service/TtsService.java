package com.arch3d.service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 语音合成（TTS）服务。
 *
 * 主路线：智谱 CogTTS（POST /paas/v4/audio/speech，音频二进制直接返回）。
 * 当智谱 Key 未配置或调用失败时，由前端自动降级到浏览器内置 speechSynthesis，
 * 后端只需返回特定错误码，不抛 500。
 */
@Slf4j
@Service
public class TtsService {

    /** 智谱 API Key：复用与视觉/绘图同一账号体系，通过环境变量注入 */
    @Value("${spring.zhipu.api-key:}")
    private String apiKey;

    @Value("${spring.zhipu.tts-url:https://open.bigmodel.cn/api/paas/v4/audio/speech}")
    private String ttsUrl;

    @Value("${spring.zhipu.tts-model:cogtts}")
    private String ttsModel;

    /** TTS 用独立 OkHttpClient：音频可能较大，超时放宽 */
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build();

    /**
     * 合成语音。
     *
     * @return 音频二进制（wav/mp3 由智谱按 response_format 决定）
     * @throws TtsUnavailableException Key 未配置或智谱侧失败（前端据此降级）
     */
    public byte[] synthesize(String text, String voice) {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            throw new TtsUnavailableException("智谱 TTS 未配置");
        }

        try {
            // 入参做基本收敛：文本长度与音色白名单由后端兜底，防止拼出异常请求
            String safeText = text.length() > 500 ? text.substring(0, 500) : text;
            String safeVoice = (voice == null || voice.isBlank()) ? "tongtong" : voice;

            String json = String.format(
                "{\"model\":\"%s\",\"input\":%s,\"voice\":%s,\"response_format\":\"wav\"}",
                ttsModel,
                com.arch3d.util.JsonEscape.quote(safeText),
                com.arch3d.util.JsonEscape.quote(safeVoice));

            Request request = new Request.Builder()
                .url(ttsUrl)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    // 余额不足/限流等统一视为"服务暂不可用"，让前端走降级
                    log.warn("智谱 TTS 调用失败: HTTP {} {}", response.code(), err.substring(0, Math.min(err.length(), 200)));
                    throw new TtsUnavailableException("智谱 TTS 暂不可用");
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new TtsUnavailableException("智谱 TTS 返回为空");
                }
                return body.bytes();
            }
        } catch (TtsUnavailableException e) {
            throw e;
        } catch (IOException e) {
            log.error("智谱 TTS 网络异常", e);
            throw new TtsUnavailableException("智谱 TTS 网络异常");
        }
    }

    /** 供前端判断是否降级的专用异常 */
    public static class TtsUnavailableException extends RuntimeException {
        public TtsUnavailableException(String message) {
            super(message);
        }
    }
}

package com.arch3d.service;

import com.arch3d.dto.CaptchaResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CaptchaService {
    private static final long EXPIRE_MILLIS = 2 * 60 * 1000L;
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    public CaptchaResponse generateCaptcha() {
        cleanupExpired();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode(4);
        long expiresAt = System.currentTimeMillis() + EXPIRE_MILLIS;
        captchaStore.put(captchaId, new CaptchaEntry(code, expiresAt));

        String svg = buildSvg(code);
        String svgDataUri = "data:image/svg+xml;base64," +
            Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

        return CaptchaResponse.builder()
            .captchaId(captchaId)
            .captchaSvg(svgDataUri)
            .expiresInSeconds(EXPIRE_MILLIS / 1000)
            .build();
    }

    public boolean verifyAndConsume(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) return false;
        CaptchaEntry entry = captchaStore.remove(captchaId);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt()) return false;
        return entry.code().equalsIgnoreCase(captchaCode.trim());
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        captchaStore.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private String randomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }

    private String buildSvg(String code) {
        int width = 132;
        int height = 44;
        return """
            <svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' viewBox='0 0 %d %d'>
              <defs>
                <linearGradient id='bg' x1='0' y1='0' x2='1' y2='1'>
                  <stop offset='0%%' stop-color='#f8fbff'/>
                  <stop offset='100%%' stop-color='#edf5ff'/>
                </linearGradient>
              </defs>
              <rect x='1' y='1' width='%d' height='%d' rx='9' ry='9' fill='url(#bg)' stroke='#9eb8de' stroke-width='1.2'/>
              <path d='M8 31 Q 32 16 56 31 T 104 31' stroke='#c5d7f1' stroke-width='1.3' fill='none' />
              <path d='M10 15 Q 40 26 70 15 T 120 15' stroke='#d6e3f6' stroke-width='1.1' fill='none' />
              <text x='16' y='30' font-family='Verdana,Arial,sans-serif' font-size='24' font-weight='700' fill='#32507b' letter-spacing='4'>%s</text>
            </svg>
            """.formatted(width, height, width, height, width - 2, height - 2, code);
    }

    private record CaptchaEntry(String code, long expiresAt) {}
}

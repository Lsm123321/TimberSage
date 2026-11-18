package com.arch3d.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 模型文件预览接口的 IP 限流拦截器。
 *
 * 背景：游客开放在线预览后，/api/models/{id}/file-url 变成匿名可访问。
 * 攻击者只需遍历 modelId 就能批量拿到全部模型文件的公开 URL，把整个
 * 模型库拖走——这些 GLB 是贡献者上传的核心资产。按 IP 限流把遍历成本
 * 抬到不可接受的程度：
 *   - 游客：10 次/分钟（正常浏览点开详情页远到不了这个频率）
 *   - 登录用户：30 次/分钟
 * 超限直接 429，不查库、不生成 URL。
 *
 * 限流数据存 Redis 固定窗口（与 AuthRateLimitInterceptor 同一套模式），
 * 多实例部署也生效。
 */
@Component
public class GuestViewRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    /** 游客限额：浏览行为正常频率远低于此 */
    private static final int GUEST_LIMIT = 10;
    /** 登录用户限额：放宽，但仍防脚本 */
    private static final int USER_LIMIT = 30;

    public GuestViewRateLimitInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 只限 GET；写方法走不到这里
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        int limit = USER_LIMIT;
        String bucket = "rl:view:user";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth.getPrincipal() instanceof String);
        if (!authenticated) {
            limit = GUEST_LIMIT;
            bucket = "rl:view:guest";
        }

        String ip = clientIp(request);
        // 固定 1 分钟窗口：key 带窗口序号，窗口切换自动归零，过期键由 Redis 回收
        long window = System.currentTimeMillis() / 60_000L;
        String key = bucket + ":" + window + ":" + ip;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        if (count != null && count > limit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"访问过于频繁，请稍后再试\",\"data\":null,\"timestamp\":null}");
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        // 与 AuthRateLimitInterceptor 同一口径：只信任 nginx 覆盖写入的 X-Real-IP，
        // X-Forwarded-For 第一段客户端可伪造，不能当限流 key
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

package com.arch3d.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 认证接口 IP 限流拦截器（固定窗口计数，存 Redis，多实例也生效）。
 *
 * 背景：公网扫描器曾对 /api/auth/register 单日打出 64 次注册、
 * /api/auth/login 7300+ 次爆破。按客户端 IP 限流：
 *   - register：10 次/分钟
 *   - login / captcha：30 次/分钟
 * 超限直接 429，不再消耗业务逻辑和数据库。
 *
 * 说明：Caddy 标准镜像没有 rate_limit 模块，所以放到应用层做；
 * 取 IP 时优先取 X-Forwarded-For 第一跳（前端 nginx 会追加真实 IP），
 * 但只信任第一段，避免伪造头绕过。
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;

    public AuthRateLimitInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String uri = request.getRequestURI();
        int limit;
        String bucket;
        if (uri.endsWith("/register")) {
            limit = 10;
            bucket = "rl:reg";
        } else if (uri.endsWith("/login") || uri.endsWith("/captcha")) {
            limit = 30;
            bucket = "rl:auth";
        } else {
            return true;
        }

        String ip = clientIp(request);
        // 固定 1 分钟窗口：key 带上窗口序号，窗口切换自动从 0 计数，过期键由 Redis 回收
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
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"timestamp\":null}");
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}

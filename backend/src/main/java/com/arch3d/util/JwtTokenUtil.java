package com.arch3d.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.prefix}")
    private String prefix;

    /** HS256 要求密钥至少 256 bit，低于这个长度直接拒绝启动 */
    private static final int MIN_SECRET_BYTES = 32;

    private SecretKey signingKey;

    /**
     * 启动时校验密钥。
     *
     * 为什么不在每次调用时判断：密钥强度是部署配置问题，应该在启动阶段就炸出来，
     * 而不是等到线上签发 token 时才失败；也不会因为一次异常而静默降级成弱密钥。
     */
    @jakarta.annotation.PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT 密钥未配置：请通过环境变量 JWT_SECRET 提供一个长度不少于 32 字节的随机字符串");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT 密钥过短（" + bytes.length + " 字节）：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节");
        }
        if (secret.toLowerCase().contains("change-this") || secret.toLowerCase().contains("your-jwt")) {
            throw new IllegalStateException("JWT 密钥仍是示例值，请在部署环境中替换为随机密钥");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    private SecretKey getSigningKey() {
        if (signingKey == null) {
            // PostConstruct 已保证不会走到这里；兜底再校验一次，防止被反射绕过
            validateSecret();
        }
        return signingKey;
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    public String generateToken(String username, String role, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
            .setSubject(username)
            .claim("role", role)
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();

        return (String) claims.get("role");
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
            log.debug("JWT token validation successful for token (first 10 chars): {}", token != null && token.length() > 10 ? token.substring(0, 10) + "..." : token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error validating JWT token: {}", ex.getMessage());
        }
        return false;
    }

    public String getJwtHeader() {
        return header;
    }

    public String getJwtPrefix() {
        return prefix;
    }
}

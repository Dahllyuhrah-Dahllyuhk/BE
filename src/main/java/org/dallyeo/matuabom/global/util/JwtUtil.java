package org.dallyeo.matuabom.global.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;

    public JwtUtil(
        @Value("${JWT_SECRET}") String secret,
        @Value("${app.jwt.access-token-seconds:3600}") long accessTokenSeconds,
        @Value("${app.jwt.refresh-token-seconds:1296000}") long refreshTokenSeconds
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET 환경변수가 없거나 32자 미만입니다. 최소 64자 랜덤 문자열을 사용하세요."
            );
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
    }

    // ── Access Token ──────────────────────────────────────────────────────────

    public String createAccessToken(String userId) {
        return build(userId, "access", accessTokenSeconds);
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    public String createRefreshToken(String userId) {
        return build(userId, "refresh", refreshTokenSeconds);
    }

    public long getRefreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    // ── 검증 ─────────────────────────────────────────────────────────────────

    /**
     * 토큰 유효성 검사 및 userId(sub) 반환.
     * @throws JwtException 토큰이 유효하지 않거나 만료된 경우
     */
    public String validateAndGetSub(String jwt) {
        return parseClaims(jwt).getSubject();
    }

    public String getUserIdFromToken(String jwt) {
        return validateAndGetSub(jwt);
    }

    /**
     * 토큰 타입 확인 (access / refresh).
     */
    public String getTokenType(String jwt) {
        return (String) parseClaims(jwt).get("type");
    }

    /**
     * 토큰 만료 시각 반환 (블랙리스트 TTL 계산용).
     */
    public Instant getExpiration(String jwt) {
        return parseClaims(jwt).getExpiration().toInstant();
    }

    /**
     * JWT ID (jti) 반환 — 블랙리스트 Redis 키로 사용.
     */
    public String getJti(String jwt) {
        return parseClaims(jwt).getId();
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private String build(String userId, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("type", type)
                .id(java.util.UUID.randomUUID().toString())  // jti — 블랙리스트 키로 사용
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    private Claims parseClaims(String jwt) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }
}

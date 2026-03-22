package org.dallyeo.matuabom.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 기반 토큰 저장소.
 *
 * 키 구조:
 *   refresh:{userId}          → refresh token 값 (TTL = refresh 유효기간)
 *   blacklist:{accessToken}   → "1"            (TTL = access token 잔여 유효기간)
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String PREFIX_REFRESH    = "refresh:";
    private static final String PREFIX_BLACKLIST  = "blacklist:";
    private static final String PREFIX_INVITE     = "invite:";
    private static final String PREFIX_TOKEN_LOCK = "token_refresh_lock:";

    private final RedisTemplate<String, String> redisTemplate;

    // ── Refresh Token ─────────────────────────────────────────────────────────

    public void saveRefreshToken(String userId, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue()
                .set(PREFIX_REFRESH + userId, refreshToken, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 저장된 refresh token과 일치하는지 검증.
     */
    public boolean isRefreshTokenValid(String userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(PREFIX_REFRESH + userId);
        return refreshToken.equals(stored);
    }

    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(PREFIX_REFRESH + userId);
    }

    // ── Token Refresh 분산 락 ──────────────────────────────────────────────────

    /**
     * Refresh Token 갱신 락 획득 (SET NX, TTL 5초).
     * 동시에 여러 요청이 들어올 때 첫 번째 요청만 갱신 수행.
     */
    public boolean acquireTokenRefreshLock(String userId) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX_TOKEN_LOCK + userId, "1", Duration.ofSeconds(5));
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseTokenRefreshLock(String userId) {
        redisTemplate.delete(PREFIX_TOKEN_LOCK + userId);
    }

    // ── Access Token 블랙리스트 ────────────────────────────────────────────────

    /**
     * 로그아웃 시 access token을 블랙리스트에 등록.
     * Redis 키: blacklist:{jti} — JWT 전체 문자열 대신 UUID(jti)만 저장해 메모리 절약.
     */
    public void blacklistAccessToken(String jti, Instant expiresAt) {
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingSeconds > 0) {
            redisTemplate.opsForValue()
                    .set(PREFIX_BLACKLIST + jti, "1", Duration.ofSeconds(remainingSeconds));
        }
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX_BLACKLIST + jti));
    }

    // ── InviteCode 캐시 ────────────────────────────────────────────────────────

    /**
     * 초대코드 → ownerUserId 캐싱 (7일 TTL).
     * 반복 조회가 많은 패턴이므로 Redis 캐싱으로 DB 부하 감소.
     */
    public void cacheInviteCode(String code, String ownerUserId) {
        redisTemplate.opsForValue()
                .set(PREFIX_INVITE + code, ownerUserId, Duration.ofDays(7));
    }

    public String getCachedInviteOwner(String code) {
        return redisTemplate.opsForValue().get(PREFIX_INVITE + code);
    }

    public void evictInviteCode(String code) {
        redisTemplate.delete(PREFIX_INVITE + code);
    }
}

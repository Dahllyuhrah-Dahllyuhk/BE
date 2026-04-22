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
 *   blacklist:{jti}           → "1"            (TTL = access token 잔여 유효기간)
 *   rf_blacklist:{jti}        → "1"            (TTL = refresh token 잔여 유효기간)
 *   prev_jti:{userId}         → 이전 rotate된 refresh jti (TTL = 10s grace window)
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String PREFIX_REFRESH      = "refresh:";
    private static final String PREFIX_BLACKLIST    = "blacklist:";
    private static final String PREFIX_RF_BLACKLIST = "rf_blacklist:";
    private static final String PREFIX_PREV_JTI     = "prev_jti:";
    private static final String PREFIX_INVITE       = "invite:";
    private static final String PREFIX_TOKEN_LOCK   = "token_refresh_lock:";
    private static final String PREFIX_AUTH_CODE    = "auth_code:";

    private static final long GRACE_WINDOW_SECONDS = 10L;

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

    /**
     * Redis 다운 시 false 반환 (블랙리스트 체크 스킵) — JWT 서명은 상위에서 이미 검증됨
     */
    public boolean isBlacklistedSafe(String jti) {
        try {
            return isBlacklisted(jti);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Redis 다운 시 true 반환 (유효한 것으로 간주) — JWT 서명은 상위에서 이미 검증됨
     */
    public boolean isRefreshTokenValidSafe(String userId, String refreshToken) {
        try {
            return isRefreshTokenValid(userId, refreshToken);
        } catch (Exception e) {
            return true;
        }
    }

    // ── Refresh Token 블랙리스트 ───────────────────────────────────────────────

    /**
     * 로그아웃 또는 rotate 시 이전 refresh token jti를 블랙리스트에 등록.
     * Redis 키: rf_blacklist:{jti} (TTL = refresh token 잔여 유효기간)
     */
    public void blacklistRefreshToken(String jti, Instant expiresAt) {
        long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingSeconds > 0) {
            redisTemplate.opsForValue()
                    .set(PREFIX_RF_BLACKLIST + jti, "1", Duration.ofSeconds(remainingSeconds));
        }
    }

    public boolean isRefreshBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX_RF_BLACKLIST + jti));
    }

    /**
     * Redis 다운 시 false 반환 (스킵) — 가용성 우선
     */
    public boolean isRefreshBlacklistedSafe(String jti) {
        try {
            return isRefreshBlacklisted(jti);
        } catch (Exception e) {
            return false;
        }
    }

    // ── prev_jti grace window ──────────────────────────────────────────────────

    /**
     * rotate 직후 이전 jti를 10초 grace window로 저장.
     * 동시 요청 race condition에서 이전 토큰을 허용하기 위한 용도.
     */
    public void savePrevJti(String userId, String jti) {
        redisTemplate.opsForValue()
                .set(PREFIX_PREV_JTI + userId, jti, Duration.ofSeconds(GRACE_WINDOW_SECONDS));
    }

    public String getPrevJti(String userId) {
        return redisTemplate.opsForValue().get(PREFIX_PREV_JTI + userId);
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

    // ── 로그인 임시 코드 (one-time auth code) ─────────────────────────────────

    /**
     * 로그인 성공 후 access token을 임시 코드로 교환하기 위해 Redis에 저장.
     * 키: auth_code:{code} → accessToken (TTL 30초, 1회용)
     */
    public void saveAuthCode(String code, String accessToken) {
        redisTemplate.opsForValue()
                .set(PREFIX_AUTH_CODE + code, accessToken, Duration.ofSeconds(30));
    }

    /**
     * 임시 코드로 access token 조회 후 즉시 삭제 (1회용).
     */
    public String consumeAuthCode(String code) {
        String key = PREFIX_AUTH_CODE + code;
        String accessToken = redisTemplate.opsForValue().get(key);
        if (accessToken != null) {
            redisTemplate.delete(key);
        }
        return accessToken;
    }
}

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
    private static final String PREFIX_GRACE        = "grace_jti:";
    private static final String PREFIX_INVITE       = "invite:";
    private static final String PREFIX_TOKEN_LOCK   = "token_refresh_lock:";
    private static final String PREFIX_AUTH_CODE    = "auth_code:";

    // 회전 직후 grace window. 이 시간 내에 도착한 이전(직전 N세대) refresh 요청은
    // 정상 동시성으로 보고 거부 대신 현재 토큰으로 수렴시킨다.
    private static final long GRACE_WINDOW_SECONDS = 30L;

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

    // ── grace window (회전된 jti → 현재 refresh token) ──────────────────────────

    /**
     * rotate 직후 "회전되어 사라진" jti를 grace 키로 기록한다.
     * 값으로 회전 결과의 "현재" refresh token을 저장해, grace 내에 도착한 이전 토큰 요청을
     * 거부하지 않고 현재 토큰으로 수렴시킬 수 있게 한다.
     * 각 jti가 독립 키(TTL 30s)라 직전 2세대 이상의 동시 회전도 자연히 관용된다.
     */
    public void markGraceJti(String oldJti, String currentRefreshToken) {
        redisTemplate.opsForValue()
                .set(PREFIX_GRACE + oldJti, currentRefreshToken, Duration.ofSeconds(GRACE_WINDOW_SECONDS));
    }

    /**
     * grace window 내에 회전된 jti면 "현재" refresh token을 반환, 아니면 null.
     */
    public String getGraceCurrentToken(String oldJti) {
        return redisTemplate.opsForValue().get(PREFIX_GRACE + oldJti);
    }

    /**
     * Redis 다운 시 null 반환 (grace 미적용) — 안전 측.
     */
    public String getGraceCurrentTokenSafe(String oldJti) {
        try {
            return getGraceCurrentToken(oldJti);
        } catch (Exception e) {
            return null;
        }
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

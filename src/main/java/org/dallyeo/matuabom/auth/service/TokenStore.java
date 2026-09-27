package org.dallyeo.matuabom.auth.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.domain.AuthCodeDoc;
import org.dallyeo.matuabom.auth.domain.GraceJtiDoc;
import org.dallyeo.matuabom.auth.domain.InviteCacheDoc;
import org.dallyeo.matuabom.auth.domain.RefreshTokenDoc;
import org.dallyeo.matuabom.auth.domain.TokenRefreshLockDoc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * MongoDB 기반 토큰 저장소 (기존 Redis 대체).
 *
 * 컬렉션:
 *   refresh_tokens        refresh:{userId}          — refresh token
 *   grace_jtis            grace_jti:{jti}           — 회전된 jti → 현재 refresh token (30s)
 *   invite_cache          invite:{code}             — code → ownerUserId (7일)
 *   token_refresh_locks   token_refresh_lock:{userId} — 갱신 분산락 (5s)
 *   auth_codes            auth_code:{code}          — 로그인 임시 코드 → accessToken (30s, 1회용)
 *
 * 만료 정확성: Mongo TTL 인덱스는 60초 주기 스윕이라 짧은 만료엔 부정확하므로,
 * 각 문서에 expiresAt(Instant)를 두고 읽기 시 애플리케이션 레벨에서 검사한다.
 * expiresAt에 걸린 TTL 인덱스(expireAfterSeconds=0)는 지연 청소만 담당한다.
 * (TTL 인덱스는 MongoConfig에서 프로그램적으로 생성)
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    // 회전 직후 grace window. 이 시간 내에 도착한 이전 refresh 요청은
    // 정상 동시성으로 보고 거부 대신 현재 토큰으로 수렴시킨다.
    private static final long GRACE_WINDOW_SECONDS = 30L;
    private static final long TOKEN_LOCK_SECONDS   = 5L;
    private static final long AUTH_CODE_SECONDS    = 30L;
    private static final long INVITE_TTL_SECONDS   = 7L * 24 * 60 * 60;

    private static final String LOCK_PREFIX = "token_refresh_lock:";

    private final MongoTemplate mongoTemplate;

    // ── Refresh Token ─────────────────────────────────────────────────────────

    public void saveRefreshToken(String userId, String refreshToken, long ttlSeconds) {
        mongoTemplate.save(new RefreshTokenDoc(userId, refreshToken, Instant.now().plusSeconds(ttlSeconds)));
    }

    /**
     * 저장된 refresh token과 일치하고 아직 만료되지 않았는지 검증.
     */
    public boolean isRefreshTokenValid(String userId, String refreshToken) {
        RefreshTokenDoc doc = mongoTemplate.findById(userId, RefreshTokenDoc.class);
        return doc != null
                && doc.getExpiresAt() != null
                && doc.getExpiresAt().isAfter(Instant.now())
                && refreshToken.equals(doc.getToken());
    }

    public void deleteRefreshToken(String userId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(userId)), RefreshTokenDoc.class);
    }

    /**
     * 저장소 다운 시 true 반환 (유효한 것으로 간주) — JWT 서명은 상위에서 이미 검증됨
     */
    public boolean isRefreshTokenValidSafe(String userId, String refreshToken) {
        try {
            return isRefreshTokenValid(userId, refreshToken);
        } catch (Exception e) {
            return true;
        }
    }

    // ── Token Refresh 분산 락 ──────────────────────────────────────────────────

    /**
     * Refresh Token 갱신 락 획득 (유니크 _id insert, TTL 5초).
     * 만료된 락은 steal 허용. 동시에 여러 요청이 들어올 때 첫 번째 요청만 갱신 수행.
     */
    public boolean acquireTokenRefreshLock(String userId) {
        String id = LOCK_PREFIX + userId;
        Instant now = Instant.now();

        // 만료된 락은 먼저 제거해 steal 허용
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(id).and("expiresAt").lte(now)),
                TokenRefreshLockDoc.class);

        try {
            mongoTemplate.insert(new TokenRefreshLockDoc(id, now.plusSeconds(TOKEN_LOCK_SECONDS)));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void releaseTokenRefreshLock(String userId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(LOCK_PREFIX + userId)),
                TokenRefreshLockDoc.class);
    }

    // ── grace window (회전된 jti → 현재 refresh token) ──────────────────────────

    /**
     * rotate 직후 "회전되어 사라진" jti를 grace 키로 기록한다.
     * 값으로 회전 결과의 "현재" refresh token을 저장해, grace 내에 도착한 이전 토큰 요청을
     * 거부하지 않고 현재 토큰으로 수렴시킬 수 있게 한다.
     */
    public void markGraceJti(String oldJti, String currentRefreshToken) {
        mongoTemplate.save(new GraceJtiDoc(oldJti, currentRefreshToken,
                Instant.now().plusSeconds(GRACE_WINDOW_SECONDS)));
    }

    /**
     * grace window 내에 회전된 jti면 "현재" refresh token을 반환, 아니면 null.
     */
    public String getGraceCurrentToken(String oldJti) {
        GraceJtiDoc doc = mongoTemplate.findById(oldJti, GraceJtiDoc.class);
        if (doc == null || doc.getExpiresAt() == null || !doc.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        return doc.getCurrentToken();
    }

    /**
     * 저장소 다운 시 null 반환 (grace 미적용) — 안전 측.
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
     * 초대코드 → ownerUserId 캐싱 (7일 TTL). 반복 조회가 많은 패턴이므로 DB 부하 감소.
     */
    public void cacheInviteCode(String code, String ownerUserId) {
        mongoTemplate.save(new InviteCacheDoc(code, ownerUserId, Instant.now().plusSeconds(INVITE_TTL_SECONDS)));
    }

    public String getCachedInviteOwner(String code) {
        InviteCacheDoc doc = mongoTemplate.findById(code, InviteCacheDoc.class);
        if (doc == null || doc.getExpiresAt() == null || !doc.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        return doc.getOwnerUserId();
    }

    public void evictInviteCode(String code) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(code)), InviteCacheDoc.class);
    }

    // ── 로그인 임시 코드 (one-time auth code) ─────────────────────────────────

    /**
     * 로그인 성공 후 access token을 임시 코드로 교환하기 위해 저장 (TTL 30초, 1회용).
     */
    public void saveAuthCode(String code, String accessToken) {
        mongoTemplate.save(new AuthCodeDoc(code, accessToken, Instant.now().plusSeconds(AUTH_CODE_SECONDS)));
    }

    /**
     * 임시 코드로 access token 조회 후 즉시 삭제 (1회용).
     * findAndRemove로 원자적 소비. 만료된 코드는 삭제만 하고 null 반환.
     */
    public String consumeAuthCode(String code) {
        AuthCodeDoc doc = mongoTemplate.findAndRemove(
                Query.query(Criteria.where("_id").is(code)), AuthCodeDoc.class);
        if (doc == null || doc.getExpiresAt() == null || !doc.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        return doc.getAccessToken();
    }
}

package org.dallyeo.matuabom.common.crypto;

import org.bson.Document;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * MongoDB Document → GoogleOAuthClientEntity 조회 시 토큰 복호화
 */
@Component
@ReadingConverter
public class EncryptedTokenReadConverter implements Converter<Document, GoogleOAuthClientEntity> {

    private final AesEncryptor encryptor;

    public EncryptedTokenReadConverter(AesEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public GoogleOAuthClientEntity convert(Document source) {
        GoogleOAuthClientEntity entity = new GoogleOAuthClientEntity();
        entity.setId(source.getString("_id"));
        entity.setUserId(source.getString("userId"));
        entity.setGoogleEmail(source.getString("googleEmail"));
        entity.setAccessToken(safeDecrypt(source.getString("accessToken")));
        entity.setAccessTokenIssuedAt(toInstant(source.getDate("accessTokenIssuedAt")));
        entity.setAccessTokenExpiresAt(toInstant(source.getDate("accessTokenExpiresAt")));
        entity.setRefreshToken(safeDecrypt(source.getString("refreshToken")));
        entity.setRefreshTokenIssuedAt(toInstant(source.getDate("refreshTokenIssuedAt")));

        List<?> scopes = source.getList("scopes", String.class);
        if (scopes != null) {
            entity.setScopes(new HashSet<>(source.getList("scopes", String.class)));
        }

        entity.setUpdatedAt(toInstant(source.getDate("updatedAt")));
        entity.setSyncToken(source.getString("syncToken"));
        entity.setWatchChannelId(source.getString("watchChannelId"));
        entity.setWatchResourceId(source.getString("watchResourceId"));
        entity.setWatchExpiresAt(toInstant(source.getDate("watchExpiresAt")));
        return entity;
    }

    private Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    /**
     * 암호화된 값이면 복호화, 평문(레거시 데이터)이면 그대로 반환.
     * AES-GCM 암호화 값은 IV(12바이트) + ciphertext를 Base64한 형태로,
     * 최소 길이 조건 + 순수 Base64 여부로 판별.
     */
    private String safeDecrypt(String value) {
        if (value == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // AES-GCM: IV 12바이트 + GCM tag 16바이트 최소 = 28바이트 이상이어야 암호화된 값
            if (decoded.length >= 28) {
                return encryptor.decrypt(value);
            }
        } catch (IllegalArgumentException ignored) {
            // Base64 디코딩 자체가 실패 → 평문
        }
        // 평문 그대로 반환 (레거시 데이터)
        return value;
    }
}

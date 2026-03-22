package org.dallyeo.matuabom.common.crypto;

import org.bson.Document;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
        entity.setAccessToken(encryptor.decrypt(source.getString("accessToken")));
        entity.setAccessTokenIssuedAt(toInstant(source.getDate("accessTokenIssuedAt")));
        entity.setAccessTokenExpiresAt(toInstant(source.getDate("accessTokenExpiresAt")));
        entity.setRefreshToken(encryptor.decrypt(source.getString("refreshToken")));
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
}

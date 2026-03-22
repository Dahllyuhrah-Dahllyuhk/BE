package org.dallyeo.matuabom.common.crypto;

import org.bson.Document;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * GoogleOAuthClientEntity → MongoDB Document 저장 시 토큰 암호화
 */
@Component
@WritingConverter
public class EncryptedTokenWriteConverter implements Converter<GoogleOAuthClientEntity, Document> {

    private final AesEncryptor encryptor;

    public EncryptedTokenWriteConverter(AesEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public Document convert(GoogleOAuthClientEntity source) {
        Document doc = new Document();
        doc.put("_id", source.getId());
        doc.put("userId", source.getUserId());
        doc.put("googleEmail", source.getGoogleEmail());
        doc.put("accessToken", encryptor.encrypt(source.getAccessToken()));
        doc.put("accessTokenIssuedAt",
                source.getAccessTokenIssuedAt() != null ? Date.from(source.getAccessTokenIssuedAt()) : null);
        doc.put("accessTokenExpiresAt",
                source.getAccessTokenExpiresAt() != null ? Date.from(source.getAccessTokenExpiresAt()) : null);
        doc.put("refreshToken", encryptor.encrypt(source.getRefreshToken()));
        doc.put("refreshTokenIssuedAt",
                source.getRefreshTokenIssuedAt() != null ? Date.from(source.getRefreshTokenIssuedAt()) : null);
        doc.put("scopes", source.getScopes() != null ? source.getScopes().stream().toList() : null);
        doc.put("updatedAt",
                source.getUpdatedAt() != null ? Date.from(source.getUpdatedAt()) : null);
        doc.put("syncToken", source.getSyncToken());
        doc.put("watchChannelId", source.getWatchChannelId());
        doc.put("watchResourceId", source.getWatchResourceId());
        doc.put("watchExpiresAt",
                source.getWatchExpiresAt() != null ? Date.from(source.getWatchExpiresAt()) : null);
        return doc;
    }
}

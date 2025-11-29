package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.repository.GoogleOAuthClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthClientService {

    private final GoogleOAuthClientRepository repo;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public void saveTokens(String userId, String googleEmail, OAuth2AuthorizedClient client) {

        GoogleOAuthClientEntity entity = repo.findByUserId(userId)
                .orElse(new GoogleOAuthClientEntity());

        entity.setId("google:" + userId);
        entity.setUserId(userId);
        entity.setGoogleEmail(googleEmail);

        entity.setAccessToken(client.getAccessToken().getTokenValue());
        entity.setAccessTokenExpiresAt(client.getAccessToken().getExpiresAt());

        if (client.getRefreshToken() != null) {
            entity.setRefreshToken(client.getRefreshToken().getTokenValue());
            entity.setRefreshTokenIssuedAt(client.getRefreshToken().getIssuedAt());
        }

        entity.setScopes(client.getAccessToken().getScopes());
        entity.setUpdatedAt(Instant.now());

        repo.save(entity);
    }

    public Optional<GoogleOAuthClientEntity> getTokens(String userId) {
        return repo.findByUserId(userId);
    }

    public boolean isLinked(String userId) {
        return repo.findByUserId(userId).isPresent();
    }

    public Optional<GoogleOAuthClientEntity> findByChannelId(String channelId) {
        return repo.findByWatchChannelId(channelId);
    }

    public GoogleOAuthClientEntity save(GoogleOAuthClientEntity entity) {
        return repo.save(entity);
    }

    /**
     * 🚀 AccessToken 만료 시 RefreshToken으로 재발급 + 자동 저장
     */
    public String refreshAccessTokenIfExpired(String userId) throws IOException {
        GoogleOAuthClientEntity entity = repo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Google account not linked"));

        Instant now = Instant.now();

        // 아직 유효한 토큰이면 그대로 사용
        if (entity.getAccessTokenExpiresAt() != null &&
                entity.getAccessTokenExpiresAt().isAfter(now.plusSeconds(60))) {
            return entity.getAccessToken();
        }

        if (entity.getRefreshToken() == null) {
            // refresh token 자체가 없으면 사용자가 다시 연동해야 함
            throw new IllegalStateException("NO_REFRESH_TOKEN");
        }

        GoogleTokenResponse response;
        try {
            response = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    entity.getRefreshToken(),
                    clientId,
                    clientSecret
            ).execute();
        } catch (Exception e) {
            // invalid_grant 등: refresh 토큰이 죽은 상태
            throw new IllegalStateException("GOOGLE_REFRESH_FAILED", e);
        }

        entity.setAccessToken(response.getAccessToken());
        if (response.getExpiresInSeconds() != null) {
            entity.setAccessTokenExpiresAt(
                    now.plusSeconds(response.getExpiresInSeconds())
            );
        } else {
            // 만약 값을 안 주면 1시간 기본값
            entity.setAccessTokenExpiresAt(
                    now.plusSeconds(3600)
            );
        }
        entity.setUpdatedAt(now);

        repo.save(entity);
        return entity.getAccessToken();
    }
}

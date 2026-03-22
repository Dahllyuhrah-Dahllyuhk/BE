package org.dallyeo.matuabom.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.calendar.repository.GoogleOAuthClientRepository;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleOAuthClientService {

    private final GoogleOAuthClientRepository repo;
    private final EventSseService eventSseService;

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
     * AccessToken 만료 시 RefreshToken으로 재발급 후 자동 저장
     */
    public String refreshAccessTokenIfExpired(String userId) throws IOException {
        GoogleOAuthClientEntity entity = repo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Google account not linked"));

        Instant now = Instant.now();

        // 아직 유효하면 그대로 사용
        if (entity.getAccessTokenExpiresAt() != null &&
                entity.getAccessTokenExpiresAt().isAfter(now.plusSeconds(60))) {
            return entity.getAccessToken();
        }

        if (entity.getRefreshToken() == null) {
            // refresh token이 없으면 사용자가 다시 로그인해야 함
            throw new IllegalStateException("NO_REFRESH_TOKEN");
        }

        GoogleTokenResponse response;
        try {
            response = new GoogleRefreshTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    entity.getRefreshToken(),
                    clientId,
                    clientSecret
            ).execute();
        } catch (Exception e) {
            // invalid_grant 등 refresh 불가능한 상태 — 사용자에게 재연동 요청 알림
            log.warn("Google token refresh failed for userId={}: {}", userId, e.getMessage());
            eventSseService.sendGoogleReauthRequired(userId);
            throw new IllegalStateException("GOOGLE_REFRESH_FAILED", e);
        }

        entity.setAccessToken(response.getAccessToken());
        if (response.getExpiresInSeconds() != null) {
            entity.setAccessTokenExpiresAt(now.plusSeconds(response.getExpiresInSeconds()));
        } else {
            // 만료 시간을 알 수 없으면 1시간 기본값
            entity.setAccessTokenExpiresAt(now.plusSeconds(3600));
        }
        entity.setUpdatedAt(now);

        repo.save(entity);
        return entity.getAccessToken();
    }
}

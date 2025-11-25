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

    /**
     * 구글 로그인 성공 시 Access/Refresh Token 전체 저장
     */
    public void saveTokens(String userId, String googleEmail, OAuth2AuthorizedClient client) {

        GoogleOAuthClientEntity entity = repo.findByUserId(userId)
                .orElse(new GoogleOAuthClientEntity());

        entity.setId("google:" + userId);
        entity.setUserId(userId);
        entity.setGoogleEmail(googleEmail);

        // 항상 최신 Access Token / 만료 시각 갱신
        entity.setAccessToken(client.getAccessToken().getTokenValue());
        entity.setAccessTokenExpiresAt(client.getAccessToken().getExpiresAt());

        // ✅ RefreshToken이 새로 오면 갱신
        if (client.getRefreshToken() != null) {
            entity.setRefreshToken(client.getRefreshToken().getTokenValue());
            entity.setRefreshTokenIssuedAt(client.getRefreshToken().getIssuedAt());
        }
        // ❗ 새로 안 왔는데, 기존에도 없으면 -> 여전히 null (최초 설정 잘못된 케이스)
        //    이 경우는 사용자가 이번에 다시 로그인해도 refresh token이 안 왔다는 뜻이라,
        //    OAuth Authorization 쪽 설정을 확인해야 함 (우리가 방금 SecurityConfig에서 해결함)

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

        // 아직 유효하면 그대로 반환
        if (entity.getAccessTokenExpiresAt() != null &&
                entity.getAccessTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return entity.getAccessToken();
        }

        if (entity.getRefreshToken() == null) {
            // ❌ 이 경우는 DB에 refreshToken 자체가 없다는 뜻.
            //    -> 사용자가 새 OAuth 동의(동기화)를 다시 해야 함.
            throw new IllegalStateException("No refresh token available");
        }

        // 🔁 토큰 갱신 요청
        GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                entity.getRefreshToken(),
                clientId,
                clientSecret
        ).execute();

        entity.setAccessToken(response.getAccessToken());
        entity.setAccessTokenExpiresAt(
                Instant.now().plusSeconds(response.getExpiresInSeconds())
        );
        entity.setUpdatedAt(Instant.now());

        repo.save(entity);
        return entity.getAccessToken();
    }
}

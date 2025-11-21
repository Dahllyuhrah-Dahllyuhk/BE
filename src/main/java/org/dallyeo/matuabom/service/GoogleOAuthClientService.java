package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.repository.GoogleOAuthClientRepository;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthClientService {

    private final GoogleOAuthClientRepository repo;

    /**
     * 구글 로그인 성공 시 Access/Refresh Token 전체 저장
     */
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
}

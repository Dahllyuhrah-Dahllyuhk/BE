package org.dallyeo.matuabom.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Data
@Document(collection = "google_oauth_clients")
public class GoogleOAuthClientEntity {

    @Id
    private String id; // google:userId 형태로 저장

    private String userId;
    private String googleEmail;

    private String accessToken;
    private Instant accessTokenIssuedAt;
    private Instant accessTokenExpiresAt;

    private String refreshToken;
    private Instant refreshTokenIssuedAt;

    private Set<String> scopes;
    private Instant updatedAt;

    private String syncToken;

    private String watchChannelId;     // X-Goog-Channel-ID
    private String watchResourceId;    // X-Goog-Resource-ID
    private Instant watchExpiresAt;    // 만료 시각
}


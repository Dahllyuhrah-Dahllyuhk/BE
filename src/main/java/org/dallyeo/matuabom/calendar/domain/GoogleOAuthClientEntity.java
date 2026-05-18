package org.dallyeo.matuabom.calendar.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Data
@Document(collection = "google_oauth_clients")
public class GoogleOAuthClientEntity {

    @Id
    private String id;

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

    private String watchChannelId;
    private String watchResourceId;
    private Instant watchExpiresAt;
}

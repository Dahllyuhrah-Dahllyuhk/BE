package org.dallyeo.matuabom.auth.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class GoogleAuthHelper {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final NetHttpTransport httpTransport;
    private final GsonFactory jsonFactory;

    private static final String APP_NAME = "Matuabom";
    private static final String REGISTRATION_ID = "google";

    public String emailOf(OAuth2User principal) {
        Object email = principal.getAttributes().get("email");
        return email == null ? principal.getName() : email.toString();
    }

    public String principalNameOf(OAuth2User principal) {
        return principal.getName();
    }

    public Calendar calendarClient(String principalName) {
        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(REGISTRATION_ID, principalName);

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("OAuth2 access token not found for user " + principalName);
        }

        var tok = client.getAccessToken();

        // Instant → Date 변환
        Instant expiresInstant = tok.getExpiresAt();
        Date expiresAt = expiresInstant != null ? Date.from(expiresInstant) : null;

        AccessToken accessToken = new AccessToken(tok.getTokenValue(), expiresAt);
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);

        return new Calendar.Builder(httpTransport, jsonFactory, initializer)
                .setApplicationName(APP_NAME)
                .build();
    }
}

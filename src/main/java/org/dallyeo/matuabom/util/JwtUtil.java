package org.dallyeo.matuabom.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenSeconds = 60 * 60;        // 1h
    private final long refreshTokenSeconds = 60L * 60 * 24; // 24h

    public JwtUtil() {
        String secret = System.getenv().getOrDefault("JWT_SECRET",
                "change-this-to-a-long-long-random-secret-change-this");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(accessTokenSeconds)))
                .signWith(key)
                .compact();
    }

//    public String createRefreshToken(String userId) {
//        return Jwts.builder()
//                .subject(userId)
//                .issuedAt(new Date())
//                .expiration(Date.from(Instant.now().plusSeconds(refreshTokenSeconds)))
//                .signWith(key)
//                .compact();
//    }

    public String validateAndGetSub(String jwt) {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(jwt)
                    .getPayload()
                    .getSubject();
    }

    public String getUserIdFromToken(String jwt) {
            return validateAndGetSub(jwt);
    }
}

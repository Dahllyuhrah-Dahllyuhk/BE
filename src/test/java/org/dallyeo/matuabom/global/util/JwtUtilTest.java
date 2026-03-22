package org.dallyeo.matuabom.global.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 32자 이상의 테스트용 시크릿
        jwtUtil = new JwtUtil(
            "test-secret-key-minimum-32-characters-long!!",
            3600L,
            86400L
        );
    }

    @Test
    @DisplayName("Access Token 생성 및 검증")
    void createAndValidateAccessToken() {
        String token = jwtUtil.createAccessToken("user123");

        String userId = jwtUtil.validateAndGetSub(token);
        String type = jwtUtil.getTokenType(token);

        assertThat(userId).isEqualTo("user123");
        assertThat(type).isEqualTo("access");
    }

    @Test
    @DisplayName("Refresh Token 생성 및 검증")
    void createAndValidateRefreshToken() {
        String token = jwtUtil.createRefreshToken("user456");

        String userId = jwtUtil.validateAndGetSub(token);
        String type = jwtUtil.getTokenType(token);

        assertThat(userId).isEqualTo("user456");
        assertThat(type).isEqualTo("refresh");
    }

    @Test
    @DisplayName("잘못된 토큰은 JwtException 발생")
    void invalidTokenThrowsException() {
        assertThatThrownBy(() -> jwtUtil.validateAndGetSub("invalid.token.value"))
            .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("jti는 토큰마다 고유해야 함")
    void jtiIsUniquePerToken() {
        String token1 = jwtUtil.createAccessToken("user1");
        String token2 = jwtUtil.createAccessToken("user1");

        assertThat(jwtUtil.getJti(token1)).isNotEqualTo(jwtUtil.getJti(token2));
    }

    @Test
    @DisplayName("만료 시각이 현재 이후여야 함")
    void expirationIsInFuture() {
        String token = jwtUtil.createAccessToken("user1");
        assertThat(jwtUtil.getExpiration(token)).isAfter(java.time.Instant.now());
    }
}

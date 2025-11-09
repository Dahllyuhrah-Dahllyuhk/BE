package org.dallyeo.matuabom.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.KakaoUserInfo;
import org.dallyeo.matuabom.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.service.UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {
    private static final String REDIRECT_URL = "http://localhost:3000/profile";
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        OAuth2AuthorizedClient client = getOAuth2AuthorizedClient(oauthToken);
        KakaoUserInfo userInfo = getKakaoUserInfo(authentication);

        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        String kakaoAccessToken = client.getAccessToken().getTokenValue();
        Instant accessTokenExpiresAt = client.getAccessToken().getExpiresAt();
        String kakaoRefreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
        Instant refreshTokenExpiresAt = client.getRefreshToken() != null ? client.getRefreshToken().getExpiresAt() : null;

        UpsertKakaoUserDto dto = UpsertKakaoUserDto.createDto(kakaoId, nickname, profileImageUrl, kakaoAccessToken,
                kakaoRefreshToken, accessTokenExpiresAt, refreshTokenExpiresAt);

        String userId = userService.upsertKakaoUser(dto);

        String accessToken = jwtUtil.createAccessToken(userId);
        addAccessTokenToCookie(response, accessToken);
        response.sendRedirect(REDIRECT_URL);
    }

    private KakaoUserInfo getKakaoUserInfo(Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();
        return objectMapper.convertValue(attributes, KakaoUserInfo.class);
    }

    private void addAccessTokenToCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie("ACCESS_TOKEN", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofMinutes(60).getSeconds());
        response.addCookie(cookie);
    }

    private OAuth2AuthorizedClient getOAuth2AuthorizedClient(OAuth2AuthenticationToken token) {
        return oAuth2AuthorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName());
    }
}

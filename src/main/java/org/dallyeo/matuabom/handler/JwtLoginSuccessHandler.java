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

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        OAuth2AuthorizedClient client = oAuth2AuthorizedClientService.loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        Map<String, Object> attributes =oauth2User.getAttributes();

        ObjectMapper objectMapper = new ObjectMapper();
        KakaoUserInfo userInfo = objectMapper.convertValue(attributes, KakaoUserInfo.class);

        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        String kakaoAccessToken = client.getAccessToken().getTokenValue();
        Instant accessTokenExpiresAt = client.getAccessToken().getExpiresAt();
        String kakaoRefreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
        Instant refreshTokenExpiresAt = client.getRefreshToken() != null ? client.getRefreshToken().getExpiresAt() : null;


        UpsertKakaoUserDto dto = UpsertKakaoUserDto.createDto(kakaoId, nickname, profileImageUrl, kakaoAccessToken, kakaoRefreshToken, accessTokenExpiresAt, refreshTokenExpiresAt);
        String userId = userService.upsertKakaoUser(dto);

        String accessToken = jwtUtil.createAccessToken(userId);
        Cookie cookie = new Cookie("ACCESS_TOKEN", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofMinutes(60).getSeconds());

        response.addCookie(cookie);
        response.sendRedirect("http://localhost:3000/profile");
    }
}

package org.dallyeo.matuabom.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.KakaoUserInfo;
import org.dallyeo.matuabom.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.service.GoogleCalendarService;
import org.dallyeo.matuabom.service.UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    // 카카오 로그인 후 이동할 페이지
    private static final String KAKAO_REDIRECT_URL = "http://localhost:3000";
    // 구글 캘린더 동기화 후 돌아갈 페이지
    private static final String GOOGLE_REDIRECT_URL = "http://localhost:3000";

    private final GoogleCalendarService googleCalendarService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        // ✅ 1) 카카오 로그인 처리
        if ("kakao".equalsIgnoreCase(registrationId)) {
            handleKakaoLogin(response, oauthToken, authentication);
            return;
        }

        // ✅ 2) 구글 로그인 처리 (카카오 유저와 구글 이메일 매핑 + 캘린더 동기화)
        if ("google".equalsIgnoreCase(registrationId)) {
            handleGoogleLogin(request, response, oauthToken, authentication);
            return;
        }

        // ✅ 그 외 provider 대비한 fallback
        response.sendRedirect(GOOGLE_REDIRECT_URL);
    }

    /* ==========================
       카카오 로그인 처리
       ========================== */
    private void handleKakaoLogin(HttpServletResponse response,
                                  OAuth2AuthenticationToken oauthToken,
                                  Authentication authentication) throws IOException {

        OAuth2AuthorizedClient client = getOAuth2AuthorizedClient(oauthToken);
        KakaoUserInfo userInfo = getKakaoUserInfo(authentication);

        Long kakaoId = userInfo.getId();
        String nickname = userInfo.getNickname();
        String profileImageUrl = userInfo.getProfileImageUrl();

        String kakaoAccessToken = client.getAccessToken().getTokenValue();
        Instant accessTokenExpiresAt = client.getAccessToken().getExpiresAt();
        String kakaoRefreshToken = client.getRefreshToken() != null
                ? client.getRefreshToken().getTokenValue()
                : null;
        Instant refreshTokenExpiresAt = client.getRefreshToken() != null
                ? client.getRefreshToken().getExpiresAt()
                : null;

        UpsertKakaoUserDto dto = UpsertKakaoUserDto.createDto(
                kakaoId,
                nickname,
                profileImageUrl,
                kakaoAccessToken,
                kakaoRefreshToken,
                accessTokenExpiresAt,
                refreshTokenExpiresAt
        );

        // MongoDB에 유저 upsert, 우리 쪽 userId 반환
        String userId = userService.upsertKakaoUser(dto);

        // userId 로 JWT 발급 후 쿠키에 저장
        String accessToken = jwtUtil.createAccessToken(userId);
        addAccessTokenToCookie(response, accessToken);

        response.sendRedirect(KAKAO_REDIRECT_URL);
    }

    private KakaoUserInfo getKakaoUserInfo(Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();
        return objectMapper.convertValue(attributes, KakaoUserInfo.class);
    }

    /* ==========================
       구글 로그인 / 동기화 처리
       ========================== */
    private void handleGoogleLogin(HttpServletRequest request,
                                   HttpServletResponse response,
                                   OAuth2AuthenticationToken oauthToken,
                                   Authentication authentication) throws IOException {

        // 1) 구글 OAuth 응답에서 이메일 추출
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        Object emailAttr = oauth2User.getAttributes().get("email");
        String googleEmail = emailAttr != null ? emailAttr.toString() : null;

        // 2) 요청 쿠키의 ACCESS_TOKEN 에서 우리 userId 추출
        String jwt = extractAccessTokenFromCookie(request);
        String userId = (jwt != null ? jwtUtil.getUserIdFromToken(jwt) : null);
        //  ↑ ↑ ↑
        // JwtUtil 에서 토큰에서 userId(subject)를 꺼내는 메서드를
        // getUserIdFromToken(String token) 이름으로 사용했다고 가정.
        // 이름이 다르면 여기만 수정하면 됨.

        // 3) 해당 유저 문서에 구글 이메일 저장 (카카오 계정 ↔ 구글 이메일 매핑)
        if (googleEmail != null && userId != null) {
            userService.linkGoogleEmail(userId, googleEmail);
        }

        // 4) 구글 캘린더 → DB 동기화 (해당 이메일 기준)
        OAuth2AuthorizedClient client = getOAuth2AuthorizedClient(oauthToken);
        try {
            googleCalendarService.fetchAndSaveAllEvents(client, googleEmail);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Google calendar sync failed", e);
        }

        // 5) 동기화 완료 후 홈으로 이동
        response.sendRedirect(GOOGLE_REDIRECT_URL);
    }

    /* ==========================
       공통 유틸
       ========================== */

    private void addAccessTokenToCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie("ACCESS_TOKEN", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 로컬 개발 환경: http
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofMinutes(60).getSeconds());
        response.addCookie(cookie);
    }

    private OAuth2AuthorizedClient getOAuth2AuthorizedClient(OAuth2AuthenticationToken token) {
        return oAuth2AuthorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        );
    }

    private String extractAccessTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie c : cookies) {
            if ("ACCESS_TOKEN".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}

package org.dallyeo.matuabom.handler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.KakaoUserInfo;
import org.dallyeo.matuabom.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.service.GoogleOAuthClientService;
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
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String KAKAO_REDIRECT_URL = "http://localhost:3000";
    private static final String GOOGLE_REDIRECT_URL = "http://localhost:3000";

    private final UserService userService;
    private final GoogleOAuthClientService googleOAuthClientService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        if ("kakao".equals(provider)) {
            handleKakaoLogin(response, oauthToken, authentication);
            return;
        }

        if ("google".equals(provider)) {
            handleGoogleLogin(request, response, oauthToken, authentication);
            return;
        }

        response.sendRedirect("http://localhost:3000");
    }


    // =====================================================
    // 1) 카카오 로그인
    // =====================================================
    private void handleKakaoLogin(
            HttpServletResponse response,
            OAuth2AuthenticationToken oauthToken,
            Authentication authentication
    ) throws IOException {

        OAuth2AuthorizedClient client =
                oAuth2AuthorizedClientService.loadAuthorizedClient("kakao", oauthToken.getName());

        KakaoUserInfo info = extractKakaoInfo(authentication);

        UpsertKakaoUserDto dto = UpsertKakaoUserDto.createDto(
                info.getId(),
                info.getNickname(),
                info.getProfileImageUrl(),
                client.getAccessToken().getTokenValue(),
                client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null,
                client.getAccessToken().getExpiresAt(),
                client.getRefreshToken() != null ? client.getRefreshToken().getExpiresAt() : null
        );

        String userId = userService.upsertKakaoUser(dto);

        // JWT 쿠키 저장
        String accessToken = jwtUtil.createAccessToken(userId);
        Cookie cookie = new Cookie("ACCESS_TOKEN", accessToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) Duration.ofHours(1).getSeconds());
        response.addCookie(cookie);

        response.sendRedirect(KAKAO_REDIRECT_URL);
    }

    private KakaoUserInfo extractKakaoInfo(Authentication auth) {
        OAuth2User user = (OAuth2User) auth.getPrincipal();
        Map<String, Object> attrs = user.getAttributes();

        // 1) id
        Long id = ((Number) attrs.get("id")).longValue();

        // 2) kakao_account 전체 맵 가져오기
        Map<String, Object> accountMap = (Map<String, Object>) attrs.get("kakao_account");

        if (accountMap == null) {
            return new KakaoUserInfo(id, null); // fallback
        }

        // 3) profile 맵
        Map<String, Object> profileMap = (Map<String, Object>) accountMap.get("profile");

        if (profileMap == null) {
            KakaoUserInfo.KakaoAccount account =
                    new KakaoUserInfo.KakaoAccount();
            return new KakaoUserInfo(id, account);
        }

        // 4) nickname + profileImage
        String nickname = (String) profileMap.get("nickname");
        String profileImg = (String) profileMap.get("profile_image_url");

        // 5) DTO 조립
        KakaoUserInfo.KakaoProfile profile = new KakaoUserInfo.KakaoProfile();
        profile.setNickname(nickname);
        profile.setProfileImageUrl(profileImg);

        KakaoUserInfo.KakaoAccount account = new KakaoUserInfo.KakaoAccount();
        account.setProfile(profile);
        account.setEmail((String) accountMap.get("email"));

        return new KakaoUserInfo(id, account);
    }


    // =====================================================
    // 2) 구글 동기화 / 연동
    // =====================================================
    private void handleGoogleLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2AuthenticationToken oauthToken,
            Authentication authentication
    ) throws IOException {

        // A. 우리 userId 가져오기
        String jwt = getCookie(request, "ACCESS_TOKEN");
        String userId = (jwt != null) ? jwtUtil.getUserIdFromToken(jwt) : null;
        if (userId == null) {
            response.sendRedirect(GOOGLE_REDIRECT_URL);
            return;
        }

        // B. 구글 이메일 확보
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = (String) oauth2User.getAttributes().get("email");

        // C. DB에 "구글 연동됨" 표시
        userService.linkGoogleEmail(userId, googleEmail);

        // D. OAuth AuthorizedClient 가져오기
        OAuth2AuthorizedClient googleClient =
                oAuth2AuthorizedClientService.loadAuthorizedClient("google", oauthToken.getName());

        // E. 토큰 DB 저장 (분리형 구조)
        googleOAuthClientService.saveTokens(userId, googleEmail, googleClient);

        // F. redispatch
        response.sendRedirect(GOOGLE_REDIRECT_URL);
    }


    // =====================================================
    // UTIL
    // =====================================================
    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}

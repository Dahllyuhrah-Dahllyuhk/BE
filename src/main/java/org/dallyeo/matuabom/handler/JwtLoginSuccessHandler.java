package org.dallyeo.matuabom.handler;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.KakaoUserInfo;
import org.dallyeo.matuabom.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.service.UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private final UserService userService;
    private final GoogleOAuthClientService googleOAuthClientService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final JwtUtil jwtUtil;
    private final org.dallyeo.matuabom.service.GoogleSyncService googleSyncService;

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
            cleanupSession(request, response);
            return;
        }

        if ("google".equals(provider)) {
            handleGoogleLogin(request, response, oauthToken, authentication);
            cleanupSession(request, response);
            return;
        }

        cleanupSession(request, response);
        response.sendRedirect(frontendBaseUrl);
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

        String accessToken = jwtUtil.createAccessToken(userId);
        ResponseCookie cookie = ResponseCookie.from("ACCESS_TOKEN", accessToken)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .maxAge(Duration.ofHours(1))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(frontendBaseUrl);
    }

    private KakaoUserInfo extractKakaoInfo(Authentication auth) {
        OAuth2User user = (OAuth2User) auth.getPrincipal();
        Map<String, Object> attrs = user.getAttributes();

        Long id = ((Number) attrs.get("id")).longValue();
        Map<String, Object> accountMap = (Map<String, Object>) attrs.get("kakao_account");

        if (accountMap == null) return new KakaoUserInfo(id, null);

        Map<String, Object> profileMap = (Map<String, Object>) accountMap.get("profile");
        if (profileMap == null) {
            KakaoUserInfo.KakaoAccount account = new KakaoUserInfo.KakaoAccount();
            return new KakaoUserInfo(id, account);
        }

        String nickname = (String) profileMap.get("nickname");
        String profileImg = (String) profileMap.get("profile_image_url");

        KakaoUserInfo.KakaoProfile profile = new KakaoUserInfo.KakaoProfile();
        profile.setNickname(nickname);
        profile.setProfileImageUrl(profileImg);

        KakaoUserInfo.KakaoAccount account = new KakaoUserInfo.KakaoAccount();
        account.setProfile(profile);
        account.setEmail((String) accountMap.get("email"));

        return new KakaoUserInfo(id, account);
    }

    // =====================================================
    // 2) 구글 연동 / 동기화
    // =====================================================
    private void handleGoogleLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2AuthenticationToken oauthToken,
            Authentication authentication
    ) throws IOException {

        String jwt = getCookie(request, "ACCESS_TOKEN");
        String userId = (jwt != null) ? jwtUtil.getUserIdFromToken(jwt) : null;

        if (userId == null) {
            response.sendRedirect(frontendBaseUrl);
            return;
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = (String) oauth2User.getAttributes().get("email");

        userService.linkGoogleEmail(userId, googleEmail);

        OAuth2AuthorizedClient googleClient =
                oAuth2AuthorizedClientService.loadAuthorizedClient("google", oauthToken.getName());

        googleOAuthClientService.saveTokens(userId, googleEmail, googleClient);

        // 🔥 runInitialSync -> runIncrementalSync 로 변경 (통합)
        googleSyncService.runIncrementalSync(userId);

        response.sendRedirect(frontendBaseUrl);
    }

    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void cleanupSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                try { session.removeAttribute("SPRING_SECURITY_CONTEXT"); } catch (Exception ignored) {}
                try { session.removeAttribute("oauth2_auth_request"); } catch (Exception ignored) {}
                try { session.removeAttribute("OAUTH2_AUTHORIZATION_REQUEST"); } catch (Exception ignored) {}
                try { session.invalidate(); } catch (IllegalStateException ignored) {}
            }

            SecurityContextHolder.clearContext();

            ResponseCookie expiredSessionCookie = ResponseCookie.from("JSESSIONID", "")
                    .path("/")
                    .httpOnly(true)
                    .maxAge(Duration.ofSeconds(0))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString());

        } catch (Exception ignored) {}
    }
}

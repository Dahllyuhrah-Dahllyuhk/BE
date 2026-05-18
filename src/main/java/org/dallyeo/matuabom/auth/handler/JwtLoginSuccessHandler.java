package org.dallyeo.matuabom.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.dto.KakaoUserInfo;
import org.dallyeo.matuabom.auth.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleCalendarService;
import org.dallyeo.matuabom.calendar.service.GoogleSyncService;
import org.dallyeo.matuabom.user.service.UserService;
import org.dallyeo.matuabom.global.util.JwtUtil;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    private final UserService userService;
    private final TokenStore tokenStore;
    private final GoogleOAuthClientService googleOAuthClientService;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final JwtUtil jwtUtil;
    private final GoogleSyncService googleSyncService;
    private final GoogleCalendarService googleCalendarService;

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

    // =========================================================================
    // 카카오 로그인
    // =========================================================================

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

        String accessToken  = jwtUtil.createAccessToken(userId);
        String refreshToken = jwtUtil.createRefreshToken(userId);

        // Refresh Token → Redis 저장 (TTL 자동 설정)
        tokenStore.saveRefreshToken(userId, refreshToken, jwtUtil.getRefreshTokenSeconds());

        // Access Token → 임시 코드로 교환 (30초 TTL, 1회용)
        String authCode = UUID.randomUUID().toString();
        tokenStore.saveAuthCode(authCode, accessToken);

        // Refresh Token만 httpOnly 쿠키로 설정
        ResponseCookie refreshCookie = ResponseCookie.from("REFRESH_TOKEN", refreshToken)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .maxAge(Duration.ofSeconds(jwtUtil.getRefreshTokenSeconds()))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        response.sendRedirect(frontendBaseUrl + "?code=" + authCode);
    }

    @SuppressWarnings("unchecked")
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

    // =========================================================================
    // 구글 계정 연동 / 캘린더 동기화
    // =========================================================================

    private void handleGoogleLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            OAuth2AuthenticationToken oauthToken,
            Authentication authentication
    ) throws IOException {
        // 1순위: SecurityContext (JwtAuthFilter가 REFRESH_TOKEN으로 인증한 경우)
        String userId = extractUserIdFromContext();

        // 2순위: 세션에 저장된 userId (구글 OAuth 리다이렉트로 SecurityContext가 교체된 경우)
        if (userId == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                userId = (String) session.getAttribute("pending_google_link_userId");
            }
        }

        if (userId == null) {
            response.sendRedirect(frontendBaseUrl + "/login");
            return;
        }

        // 세션에서 임시 userId 제거
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("pending_google_link_userId");
        }

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String googleEmail = (String) oauth2User.getAttributes().get("email");

        userService.linkGoogleEmail(userId, googleEmail);

        OAuth2AuthorizedClient googleClient =
                oAuth2AuthorizedClientService.loadAuthorizedClient("google", oauthToken.getName());

        googleOAuthClientService.saveTokens(userId, googleEmail, googleClient);

        googleOAuthClientService.getTokens(userId).ifPresent(tokens ->
                googleCalendarService.ensureWatchChannel(tokens)
        );

        googleSyncService.runIncrementalSync(userId);

        // 구글 연동 후 리다이렉트 시에도 access token을 재발급해서 전달
        // (OAuth 리다이렉트로 FE 메모리의 access token이 날아가므로)
        String accessToken = jwtUtil.createAccessToken(userId);
        String authCode = UUID.randomUUID().toString();
        tokenStore.saveAuthCode(authCode, accessToken);

        response.sendRedirect(frontendBaseUrl + "?code=" + authCode);
    }

    /**
     * SecurityContext에서 인증된 사용자의 userId를 추출.
     * JwtAuthFilter가 REFRESH_TOKEN으로 인증을 설정해두므로 이를 활용.
     */
    private String extractUserIdFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof org.dallyeo.matuabom.auth.security.CustomPrincipal cp) {
            return cp.getUserId();
        }
        return null;
    }

    // =========================================================================
    // 세션 정리
    // =========================================================================

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

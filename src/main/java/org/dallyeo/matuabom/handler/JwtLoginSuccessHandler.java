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
            // 세션/보안 컨텍스트 정리
            cleanupSession(request, response);
            return;
        }

        if ("google".equals(provider)) {
            handleGoogleLogin(request, response, oauthToken, authentication);
            // 세션/보안 컨텍스트 정리
            cleanupSession(request, response);
            return;
        }

        // fallback
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

        // JWT 쿠키 저장
        String accessToken = jwtUtil.createAccessToken(userId);
        ResponseCookie cookie = ResponseCookie.from("ACCESS_TOKEN", accessToken)
            .path("/")
            .httpOnly(true) // JS에서 접근 불가 (보안)
            .secure(true)  // 💡 로컬(http)에서는 false여야 함
            .maxAge(Duration.ofHours(1))
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.sendRedirect(frontendBaseUrl);
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
            response.sendRedirect(frontendBaseUrl);
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

        googleSyncService.runInitialSync(userId);

        // F. redispatch
        response.sendRedirect(frontendBaseUrl);
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

    /**
     * 로그인(소셜) 완료 후 서버 세션과 스프링 시큐리티 컨텍스트를 정리합니다.
     * - HttpSession이 존재하면 SPRING_SECURITY_CONTEXT와 OAuth2 요청 객체를 제거하고 invalidate합니다.
     * - SecurityContextHolder를 clear 합니다.
     * - JSESSIONID 같은 세션 쿠키는 만료시켜 클라이언트에서도 제거되도록 합니다.
     */
    private void cleanupSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 1) HttpSession 정리
            HttpSession session = request.getSession(false);
            if (session != null) {
                // 스프링 시큐리티 컨텍스트 제거
                try {
                    session.removeAttribute("SPRING_SECURITY_CONTEXT");
                } catch (Exception ignored) {}

                // OAuth2 authorization request가 세션에 남아있을 수 있으니 제거
                // 프레임워크 버전/구성에 따라 이름이 다를 수 있으므로 몇 가지 후보를 제거
                try { session.removeAttribute("oauth2_auth_request"); } catch (Exception ignored) {}
                try { session.removeAttribute("OAUTH2_AUTHORIZATION_REQUEST"); } catch (Exception ignored) {}
                try { session.removeAttribute("org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_ATTR_NAME"); } catch (Exception ignored) {}

                // 세션 무효화
                try { session.invalidate(); } catch (IllegalStateException ignored) {}
            }

            // 2) SecurityContext 정리
            SecurityContextHolder.clearContext();

            // 3) 세션 쿠키 만료 (JSESSIONID 등)
            ResponseCookie expiredSessionCookie = ResponseCookie.from("JSESSIONID", "")
                    .path("/")
                    .httpOnly(true)
                    .maxAge(Duration.ofSeconds(0))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString());

            // 4) 만약 OAuth2 관련 임시 쿠키가 있다면 만료
            ResponseCookie expiredOAuth2Cookie = ResponseCookie.from("OAUTH2_AUTH_REQUEST", "")
                    .path("/")
                    .httpOnly(true)
                    .maxAge(Duration.ofSeconds(0))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredOAuth2Cookie.toString());

        } catch (Exception e) {
            // 실패하더라도 로그인 성공 로직을 방해하지 않도록 swallow
            // (원하면 로깅 추가 가능)
        }
    }
}

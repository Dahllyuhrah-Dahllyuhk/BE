package org.dallyeo.matuabom.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.global.util.JwtUtil;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.dto.MeDto;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.dallyeo.matuabom.user.service.UserWithdrawalService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserJpaRepository userRepository;
    private final TokenStore tokenStore;
    private final JwtUtil jwtUtil;
    private final UserWithdrawalService userWithdrawalService;
    private final org.dallyeo.matuabom.user.service.UserService userService;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Principal is null");
        }
        UserEntity user = userRepository.findByMongoId(principal.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        return ResponseEntity.ok(MeDto.createDto(user));
    }

    /**
     * 약관/개인정보처리방침 동의를 서버에 기록한다 (최초 1회).
     */
    @PostMapping("/api/auth/terms")
    public ResponseEntity<Void> agreeTerms(@AuthenticationPrincipal CustomPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userService.agreeTerms(principal.getUserId());
        return ResponseEntity.ok().build();
    }

    /**
     * 로그인 직후 FE가 임시 코드를 Access Token으로 교환하는 API.
     * 코드는 30초 TTL + 1회용으로 Redis에 저장되어 있음.
     */
    @PostMapping("/api/auth/token")
    public ResponseEntity<?> exchangeToken(@org.springframework.web.bind.annotation.RequestParam String code) {
        String accessToken = tokenStore.consumeAuthCode(code);
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_or_expired_code"));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("accessToken", accessToken));
    }

    /**
     * 구글 캘린더 연동 시작 전 호출.
     * OAuth 리다이렉트 후 SecurityContext가 교체되므로 세션에 userId를 미리 저장.
     */
    @PostMapping("/api/auth/prepare-google-link")
    public ResponseEntity<Void> prepareGoogleLink(
            HttpServletRequest request,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("pending_google_link_userId", principal.getUserId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        // ① Refresh Token → rf_blacklist 등록 + Redis 삭제
        String refreshToken = extractCookie(request, "REFRESH_TOKEN");
        if (refreshToken != null) {
            try {
                tokenStore.blacklistRefreshToken(jwtUtil.getJti(refreshToken), jwtUtil.getExpiration(refreshToken));
            } catch (Exception e) {
                log.warn("Failed to blacklist refresh token: {}", e.getMessage());
            }
        }
        if (principal != null) {
            tokenStore.deleteRefreshToken(principal.getUserId());
        }

        // ② Refresh Token 쿠키 삭제, ACCESS_TOKEN 잔여 쿠키도 정리
        expireCookie(response, "REFRESH_TOKEN");
        expireCookie(response, "ACCESS_TOKEN");

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/auth/me")
    public ResponseEntity<Void> withdraw(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        userWithdrawalService.withdraw(principal.getUserId());

        // Refresh Token 쿠키 삭제, ACCESS_TOKEN 잔여 쿠키도 정리
        expireCookie(response, "REFRESH_TOKEN");
        expireCookie(response, "ACCESS_TOKEN");

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();

        return ResponseEntity.noContent().build();
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────────

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void expireCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

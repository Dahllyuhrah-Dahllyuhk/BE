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

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserJpaRepository userRepository;
    private final TokenStore tokenStore;
    private final JwtUtil jwtUtil;
    private final UserWithdrawalService userWithdrawalService;

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

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        // ① Access Token → Redis 블랙리스트 등록 (잔여 TTL만큼)
        String accessToken = extractCookie(request, "ACCESS_TOKEN");
        if (accessToken != null) {
            try {
                tokenStore.blacklistAccessToken(jwtUtil.getJti(accessToken), jwtUtil.getExpiration(accessToken));
            } catch (Exception e) {
                log.warn("Failed to blacklist access token: {}", e.getMessage());
            }
        }

        // ② Refresh Token → Redis에서 삭제
        if (principal != null) {
            tokenStore.deleteRefreshToken(principal.getUserId());
        }

        // ③ 쿠키 삭제 (Max-Age=0)
        expireCookie(response, "ACCESS_TOKEN");
        expireCookie(response, "REFRESH_TOKEN");

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

        String accessToken = extractCookie(request, "ACCESS_TOKEN");

        userWithdrawalService.withdraw(principal.getUserId(), accessToken);

        // 쿠키 삭제
        expireCookie(response, "ACCESS_TOKEN");
        expireCookie(response, "REFRESH_TOKEN");

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

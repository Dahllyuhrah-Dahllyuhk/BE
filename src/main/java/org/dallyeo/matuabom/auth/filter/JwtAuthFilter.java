package org.dallyeo.matuabom.auth.filter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.global.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;

    @Value("${app.cookie-secure:false}")
    private boolean cookieSecure;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String accessToken  = extractCookie(request, "ACCESS_TOKEN");
        String refreshToken = extractCookie(request, "REFRESH_TOKEN");

        // ① Access Token 유효 + 블랙리스트에 없는 경우 → 정상 인증
        if (accessToken != null) {
            try {
                boolean blacklisted = false;
                try {
                    blacklisted = tokenStore.isBlacklisted(jwtUtil.getJti(accessToken));
                } catch (Exception redisEx) {
                    log.warn("Redis unavailable during blacklist check, allowing request. path={}", request.getRequestURI());
                }

                if (blacklisted) {
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = jwtUtil.validateAndGetSub(accessToken);

                // 토큰 타입 검증 — refresh token으로 API 호출 차단
                if (!"access".equals(jwtUtil.getTokenType(accessToken))) {
                    log.warn("Non-access token used as access token. path={}", request.getRequestURI());
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                setAuthentication(request, userId);
                filterChain.doFilter(request, response);
                return;

            } catch (JwtException e) {
                log.debug("Access token expired, trying refresh. path={}", request.getRequestURI());
            }
        }

        // ② Access Token 없거나 만료 → Refresh Token으로 무중단 재발급
        if (refreshToken != null) {
            try {
                String userId = jwtUtil.validateAndGetSub(refreshToken);

                // 토큰 타입 검증
                if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
                    log.warn("Non-refresh token used as refresh token. path={}", request.getRequestURI());
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                if (!tokenStore.isRefreshTokenValid(userId, refreshToken)) {
                    log.warn("Refresh token mismatch for userId={}. Possible token theft.", userId);
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 분산 락 획득 — 동시 요청 중 첫 번째만 토큰 갱신
                if (tokenStore.acquireTokenRefreshLock(userId)) {
                    try {
                        String newAccessToken  = jwtUtil.createAccessToken(userId);
                        String newRefreshToken = jwtUtil.createRefreshToken(userId);

                        tokenStore.saveRefreshToken(userId, newRefreshToken, jwtUtil.getRefreshTokenSeconds());

                        addCookie(response, "ACCESS_TOKEN",  newAccessToken,  (int) jwtUtil.getRefreshTokenSeconds());
                        addCookie(response, "REFRESH_TOKEN", newRefreshToken, (int) jwtUtil.getRefreshTokenSeconds());

                        log.debug("Token refreshed silently for userId={}", userId);
                    } finally {
                        tokenStore.releaseTokenRefreshLock(userId);
                    }
                } else {
                    // 락 획득 실패 — 다른 요청이 이미 갱신 중, 현재 토큰으로 인증만 수행
                    log.debug("Token refresh lock not acquired for userId={}, skipping rotation", userId);
                }

                setAuthentication(request, userId);

            } catch (JwtException e) {
                log.debug("Refresh token also expired. Re-login required.");
                clearContext(response);
            }
        }

        filterChain.doFilter(request, response);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private void setAuthentication(HttpServletRequest request, String userId) {
        CustomPrincipal principal = new CustomPrincipal(userId);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.emptyList()
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearContext(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .maxAge(Duration.ofSeconds(maxAge))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

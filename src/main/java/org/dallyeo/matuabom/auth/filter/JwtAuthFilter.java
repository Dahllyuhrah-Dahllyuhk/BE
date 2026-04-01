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
                boolean blacklisted = tokenStore.isBlacklistedSafe(jwtUtil.getJti(accessToken));
                if (blacklisted) {
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = jwtUtil.validateAndGetSub(accessToken);

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

                if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
                    log.warn("Non-refresh token used as refresh token. path={}", request.getRequestURI());
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 락을 먼저 획득 후 토큰 검증 — 동시 요청 race condition 방지
                if (tokenStore.acquireTokenRefreshLock(userId)) {
                    try {
                        // 락 안에서 검증 — 다른 요청이 이미 rotate했으면 mismatch이지만 정상
                        if (!tokenStore.isRefreshTokenValidSafe(userId, refreshToken)) {
                            // 이미 다른 요청이 rotate 완료한 케이스 → 인증만 허용, 재발급 생략
                            log.debug("Refresh token already rotated for userId={}, skipping rotation", userId);
                        } else {
                            String newAccessToken  = jwtUtil.createAccessToken(userId);
                            String newRefreshToken = jwtUtil.createRefreshToken(userId);

                            tokenStore.saveRefreshToken(userId, newRefreshToken, jwtUtil.getRefreshTokenSeconds());

                            addCookie(response, "ACCESS_TOKEN",  newAccessToken,  (int) jwtUtil.getRefreshTokenSeconds());
                            addCookie(response, "REFRESH_TOKEN", newRefreshToken, (int) jwtUtil.getRefreshTokenSeconds());

                            log.debug("Token refreshed silently for userId={}", userId);
                        }
                    } finally {
                        tokenStore.releaseTokenRefreshLock(userId);
                    }
                } else {
                    // 락 획득 실패 — 다른 요청이 이미 갱신 중, 인증만 수행
                    log.debug("Token refresh lock not acquired for userId={}, skipping rotation", userId);
                }

                setAuthentication(request, userId);

            } catch (JwtException e) {
                log.debug("Refresh token also expired. Re-login required.");
                // 실제 탈취 의심 케이스(토큰이 유효하지만 DB 불일치)는 락 안에서 처리되므로
                // 여기는 만료 케이스만 도달함
                clearContext(response);
            }
        }

        filterChain.doFilter(request, response);
    }

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

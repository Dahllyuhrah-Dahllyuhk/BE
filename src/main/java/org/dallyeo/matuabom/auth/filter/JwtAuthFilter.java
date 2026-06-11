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

        String accessToken  = extractBearerToken(request);
        String refreshToken = extractCookie(request, "REFRESH_TOKEN");

        // ① Access Token 유효 → 정상 인증
        if (accessToken != null) {
            try {
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
                String userId    = jwtUtil.validateAndGetSub(refreshToken);
                String refreshJti = jwtUtil.getJti(refreshToken);

                if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
                    log.warn("Non-refresh token used as refresh token. path={}", request.getRequestURI());
                    clearContext(response);
                    filterChain.doFilter(request, response);
                    return;
                }

                // ── 분산 락 획득 ───────────────────────────────────────────
                if (tokenStore.acquireTokenRefreshLock(userId)) {
                    try {
                        if (tokenStore.isRefreshTokenValidSafe(userId, refreshToken)) {
                            // 정상: 현재 토큰 → rotate
                            String newAccessToken  = jwtUtil.createAccessToken(userId);
                            String newRefreshToken = jwtUtil.createRefreshToken(userId);

                            // 회전되어 사라진 jti를 grace window에 기록(값=새 토큰) — 직전 토큰을 든 정상 동시 요청을 현재 토큰으로 수렴
                            tokenStore.markGraceJti(refreshJti, newRefreshToken);
                            tokenStore.saveRefreshToken(userId, newRefreshToken, jwtUtil.getRefreshTokenSeconds());

                            addCookie(response, "REFRESH_TOKEN", newRefreshToken, (int) jwtUtil.getRefreshTokenSeconds());
                            response.setHeader("X-New-Access-Token", newAccessToken);
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                            log.debug("Token rotated for userId={}", userId);
                        } else {
                            // 현재 토큰 아님 → grace 확인 (최근 30s 내 회전된 토큰인가)
                            String currentRefresh = tokenStore.getGraceCurrentTokenSafe(refreshJti);
                            if (currentRefresh != null) {
                                // 정상 동시성: 거부하지 않고 현재 토큰 쿠키로 수렴 + 새 access 발급 (재회전 X)
                                log.debug("Grace hit — converging to current token. userId={}", userId);
                                addCookie(response, "REFRESH_TOKEN", currentRefresh, (int) jwtUtil.getRefreshTokenSeconds());
                                response.setHeader("X-New-Access-Token", jwtUtil.createAccessToken(userId));
                                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                            } else {
                                // grace window 밖의 옛 토큰 → 진짜 폐기/탈취 의심
                                log.warn("Stale refresh token outside grace window. userId={}", userId);
                                clearContext(response);
                                filterChain.doFilter(request, response);
                                return;
                            }
                        }
                    } finally {
                        tokenStore.releaseTokenRefreshLock(userId);
                    }

                } else {
                    // 락 미획득 — 다른 요청이 회전 중. 현재/ grace 토큰이면 회전 없이 인증만 유지.
                    boolean current = tokenStore.isRefreshTokenValidSafe(userId, refreshToken);
                    boolean grace   = tokenStore.getGraceCurrentTokenSafe(refreshJti) != null;
                    if (!current && !grace) {
                        log.warn("Stale refresh token on lock miss. userId={}", userId);
                        clearContext(response);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    log.debug("Lock miss — token current/grace, authenticating without rotation. userId={}", userId);
                }

                setAuthentication(request, userId);

            } catch (JwtException e) {
                log.debug("Refresh token expired or invalid. Re-login required.");
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

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        // EventSource(SSE)는 커스텀 헤더 불가 → SSE 경로에 한해 ?token= 쿼리의 access token 허용.
        // 이렇게 하면 SSE가 access token으로 인증(branch ①)되어 refresh 회전을 유발하지 않는다.
        if (request.getRequestURI().startsWith("/api/sse/")) {
            String queryToken = request.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }
        return null;
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

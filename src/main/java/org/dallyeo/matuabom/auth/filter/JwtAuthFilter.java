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

        // ① Access Token 유효 + 블랙리스트에 없는 경우 → 정상 인증
        if (accessToken != null) {
            try {
                String jti = jwtUtil.getJti(accessToken);

                if (tokenStore.isBlacklistedSafe(jti)) {
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
                        // 1. 명시적 폐기(로그아웃) 확인 — rf_blacklist에 있으면 차단
                        if (tokenStore.isRefreshBlacklistedSafe(refreshJti)) {
                            log.warn("Blacklisted refresh token used. userId={}", userId);
                            clearContext(response);
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // 2. Redis 현재값과 불일치 확인
                        if (!tokenStore.isRefreshTokenValidSafe(userId, refreshToken)) {
                            String prevJti = tokenStore.getPrevJti(userId);
                            if (refreshJti.equals(prevJti)) {
                                // 동시 회전 race condition — grace window 내 이전 토큰
                                log.debug("Grace window hit for userId={}, allowing request", userId);
                                setAuthentication(request, userId);
                                filterChain.doFilter(request, response);
                                return;
                            }
                            // 진짜 탈취 의심
                            log.warn("Refresh token mismatch. Possible token theft. userId={}", userId);
                            clearContext(response);
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // 3. 정상 rotate
                        String newAccessToken  = jwtUtil.createAccessToken(userId);
                        String newRefreshToken = jwtUtil.createRefreshToken(userId);

                        // 이전 jti를 grace window(10s)에 저장
                        tokenStore.savePrevJti(userId, refreshJti);
                        // 이전 refresh token을 블랙리스트에 등록 (잔여 TTL)
                        tokenStore.blacklistRefreshToken(refreshJti, jwtUtil.getExpiration(refreshToken));
                        // 새 refresh token 저장
                        tokenStore.saveRefreshToken(userId, newRefreshToken, jwtUtil.getRefreshTokenSeconds());

                        addCookie(response, "REFRESH_TOKEN", newRefreshToken, (int) jwtUtil.getRefreshTokenSeconds());
                        // 새 Access Token은 응답 헤더로 전달 (JS가 메모리에 저장)
                        response.setHeader("X-New-Access-Token", newAccessToken);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

                        log.debug("Token rotated for userId={}", userId);

                    } finally {
                        tokenStore.releaseTokenRefreshLock(userId);
                    }

                } else {
                    // 락 획득 실패 — 다른 요청이 이미 rotate 중
                    // rf_blacklist 확인 후 grace window로 처리
                    if (tokenStore.isRefreshBlacklistedSafe(refreshJti)) {
                        // 이미 rotate 완료되어 블랙리스트에 등록된 이전 토큰
                        String prevJti = tokenStore.getPrevJti(userId);
                        if (refreshJti.equals(prevJti)) {
                            log.debug("Lock miss + grace window hit for userId={}", userId);
                            setAuthentication(request, userId);
                            filterChain.doFilter(request, response);
                            return;
                        }
                        log.warn("Blacklisted refresh token used (no lock). userId={}", userId);
                        clearContext(response);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    log.debug("Token refresh lock not acquired for userId={}, skipping rotation", userId);
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

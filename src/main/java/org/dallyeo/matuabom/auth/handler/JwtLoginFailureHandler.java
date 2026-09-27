package org.dallyeo.matuabom.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * OAuth 로그인 실패 시 처리.
 *
 * 기본 실패 핸들러는 백엔드의 {@code /login?error}로 리다이렉트하는데,
 * 이 앱은 API 서버라 그 경로가 없어 정적리소스 500이 발생한다.
 * 대신 프론트엔드의 로그인 페이지로 리다이렉트한다.
 */
@Slf4j
@Component
public class JwtLoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        log.warn("OAuth 로그인 실패: {}", exception.getMessage());
        cleanupSession(request, response);
        response.sendRedirect(frontendBaseUrl + "/login?error=oauth");
    }

    private void cleanupSession(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                try {
                    session.invalidate();
                } catch (IllegalStateException ignored) {
                }
            }
            SecurityContextHolder.clearContext();

            ResponseCookie expiredSessionCookie = ResponseCookie.from("JSESSIONID", "")
                    .path("/")
                    .httpOnly(true)
                    .maxAge(Duration.ofSeconds(0))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredSessionCookie.toString());
        } catch (Exception ignored) {
        }
    }
}

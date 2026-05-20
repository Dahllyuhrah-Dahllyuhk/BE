package org.dallyeo.matuabom.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminSecretFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String ADMIN_SECRET_HEADER = "X-Admin-Secret";

    @Value("${app.admin-secret:}")
    private String adminSecret;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String requestSecret = request.getHeader(ADMIN_SECRET_HEADER);

        if (!isValidSecret(requestSecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"forbidden\",\"message\":\"관리자 인증 정보가 올바르지 않습니다.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isValidSecret(String requestSecret) {
        if (adminSecret == null || adminSecret.isBlank()) return false;
        if (requestSecret == null || requestSecret.isBlank()) return false;

        byte[] expected = adminSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = requestSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}

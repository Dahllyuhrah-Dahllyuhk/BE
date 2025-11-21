package org.dallyeo.matuabom.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.security.CustomPrincipal; // ✅ CustomPrincipal 임포트 필수
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. 쿠키에서 ACCESS_TOKEN 추출
        String token = null;
        if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                    .filter(c -> "ACCESS_TOKEN".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 2. 토큰이 있다면 검증 및 인증 객체 생성
        if (token != null) {
            try {
                // 토큰 검증 후 userId 추출
                String userId = jwtUtil.validateAndGetSub(token);

                // ✅ 핵심 수정: String 대신 CustomPrincipal 객체 생성
                CustomPrincipal principal = new CustomPrincipal(userId);

                // ✅ 인증 토큰 생성 시 principal 객체를 넣음
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,                  // principal (사용자 정보 객체)
                        null,                       // credentials (비밀번호 등, JWT라 불필요)
                        Collections.emptyList()     // authorities (권한 목록, 필요시 추가)
                );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContext에 저장 (이제 전역에서 사용 가능)
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                // 토큰 검증 실패 시 인증 정보 초기화 (안전장치)
                SecurityContextHolder.clearContext();
            }
        }

        // 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
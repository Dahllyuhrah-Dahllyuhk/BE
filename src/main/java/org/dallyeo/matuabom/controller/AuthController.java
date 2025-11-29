package org.dallyeo.matuabom.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.MeDto;
import org.dallyeo.matuabom.repository.UserRepository;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomPrincipal principal) {

        // 1. 필터가 넣어준 객체가 제대로 들어왔는지 확인
        if (principal == null) {
            // 필터는 통과했는데 여기가 null이면, 타입이 안 맞아서 주입이 안 된 것임
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Principal is null");
        }

        // 2. principal에서 userId 꺼내기
        String userId = principal.getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));

        return ResponseEntity.ok().body(MeDto.createDto(user));
    }

    @PostMapping("/api/auth/logout")
        public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {

            // 쿠키 삭제를 위해 Max-Age를 0으로 설정
            ResponseCookie cookie = ResponseCookie.from("ACCESS_TOKEN", "")
                    .path("/")
                    .httpOnly(true)
                    .secure(true) // ⚠️ JwtLoginSuccessHandler와 동일하게 설정해야 함! (현재 true)
                    .maxAge(0)    // 0초 = 즉시 삭제
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }

            return ResponseEntity.ok().build();
        }
}
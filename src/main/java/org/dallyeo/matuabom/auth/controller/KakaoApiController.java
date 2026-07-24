package org.dallyeo.matuabom.auth.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.user.service.UserService;
import org.dallyeo.matuabom.global.util.JwtUtil;
import org.springframework.http.*;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequiredArgsConstructor
public class KakaoApiController {
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    /**
     * 카카오 친구 목록 API.
     * TODO: 카카오 OAuth 토큰은 현재 어디에도 저장되지 않음.
     *       추후 암호화 저장소(예: google_oauth_clients 방식) 분리 후 복원 필요.
     *       현재는 기능 비활성화.
     */
    @GetMapping("/kakao/friends")
    public ResponseEntity<String> friends(@AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body("{\"error\":\"카카오 친구 목록은 현재 준비 중입니다.\"}");
    }
}
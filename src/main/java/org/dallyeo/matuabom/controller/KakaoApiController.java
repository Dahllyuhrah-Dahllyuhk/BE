package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.dallyeo.matuabom.service.UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequiredArgsConstructor
public class KakaoApiController {
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @GetMapping("/kakao/friends")
    public ResponseEntity<String> friends(@AuthenticationPrincipal CustomPrincipal customPrincipal) {
        User user = userService.findById(customPrincipal.getUserId());
        String accessToken = user.getKakaoAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = restTemplate.exchange("https://kapi.kakao.com/v1/api/talk/friends", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}

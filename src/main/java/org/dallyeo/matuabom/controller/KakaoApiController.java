package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.service.UserService;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.http.*;
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
    public ResponseEntity<String> friends(@CookieValue(value = "ACCESS_TOKEN",required = false)String jwt) {
        if(jwt==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No jwt token");
        String userId;
        try {
            userId = jwtUtil.validateAndGetSub(jwt);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        User user = userService.findById(userId);
        String accessToken = user.getKakaoAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = restTemplate.exchange("https://kapi.kakao.com/v1/api/talk/friends", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}

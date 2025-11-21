package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.MeDto;
import org.dallyeo.matuabom.repository.UserRepository;
import org.dallyeo.matuabom.security.CustomPrincipal; // ✅ 임포트 필수
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomPrincipal principal) { // 👈 여기를 수정해야 함!

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
}
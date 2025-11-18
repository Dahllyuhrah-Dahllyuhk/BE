package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.MeDto;
import org.dallyeo.matuabom.repository.UserRepository;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.dallyeo.matuabom.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomPrincipal customPrincipal) {
        if (customPrincipal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        User user = userRepository.findById(customPrincipal.getUserId()).orElseThrow(() ->new UsernameNotFoundException("user not found"));
        return ResponseEntity.ok().body(MeDto.createDto(user));
    }
}

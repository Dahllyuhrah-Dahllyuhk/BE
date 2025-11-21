package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;


    public User findById(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("no user"+userId));
    }

    @Transactional
    public String upsertKakaoUser(UpsertKakaoUserDto dto) {
        User user = userRepository.findByKakaoId(dto.getKakaoId())
                .map(u -> {
                    u.updateKakaoProfile(dto.getNickname(), dto.getProfileImageUrl());
                    u.updateKakaoTokens(
                            dto.getKakaoAccessToken(),
                            dto.getKakaoAccessTokenExpiresAt(),
                            dto.getKakaoRefreshToken(),
                            dto.getKakaoRefreshTokenExpiresAt());
                    return userRepository.save(u);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .kakaoId(dto.getKakaoId())
                                .nickname(dto.getNickname())
                                .profileImageUrl(dto.getProfileImageUrl())
                                .kakaoAccessToken(dto.getKakaoAccessToken())
                                .kakaoAccessTokenExpiresAt(dto.getKakaoAccessTokenExpiresAt())
                                .kakaoRefreshToken(dto.getKakaoRefreshToken())
                                .kakaoRefreshTokenExpiresAt(dto.getKakaoRefreshTokenExpiresAt())
                                .build()
                ));
        return user.getId();
    }

    @Transactional
    public void linkGoogleEmail(String userId, String googleEmail) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.setGoogleEmail(googleEmail);
        userRepository.save(user);
    }

    /** API 호출 시, 현재 카카오 유저의 구글 이메일 조회 */
    @Transactional(readOnly = true)
    public String getGoogleEmail(String userId) {
        return userRepository.findById(userId)
            .map(User::getGoogleEmail)
            .orElse(null);
    }
}
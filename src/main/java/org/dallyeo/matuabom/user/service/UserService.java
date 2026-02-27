package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository userJpaRepository;

    public UserEntity findById(String userId) {
        // userId는 mongoId(문자열) 기준으로 조회 (마이그레이션 기간 동안 호환)
        return userJpaRepository.findByMongoId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + userId));
    }

    /**
     * 카카오 로그인/회원가입 upsert.
     * @return mongoId (문자열 userId — 기존 Meeting 등과의 호환 유지)
     */
    @Transactional
    public String upsertKakaoUser(UpsertKakaoUserDto dto) {
        UserEntity user = userJpaRepository.findByKakaoId(dto.getKakaoId())
                .map(u -> {
                    u.setNickname(dto.getNickname());
                    u.setProfileImageUrl(dto.getProfileImageUrl());
                    // OAuth 토큰은 User 엔티티에서 제거 — 필요 시 별도 암호화 저장소 사용
                    return userJpaRepository.save(u);
                })
                .orElseGet(() -> {
                    UserEntity newUser = UserEntity.builder()
                            .kakaoId(dto.getKakaoId())
                            .nickname(dto.getNickname())
                            .profileImageUrl(dto.getProfileImageUrl())
                            .build();
                    UserEntity saved = userJpaRepository.save(newUser);
                    // mongoId를 Long ID의 문자열로 설정 (기존 코드와 호환)
                    saved.setMongoId(String.valueOf(saved.getId()));
                    return userJpaRepository.save(saved);
                });

        return user.getMongoId() != null ? user.getMongoId() : String.valueOf(user.getId());
    }

    @Transactional
    public void linkGoogleEmail(String userId, String googleEmail) {
        UserEntity user = findById(userId);
        user.setGoogleEmail(googleEmail);
        user.setGoogleLinked(true);
        userJpaRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String getGoogleEmail(String userId) {
        return userJpaRepository.findByMongoId(userId)
                .map(UserEntity::getGoogleEmail)
                .orElse(null);
    }
}

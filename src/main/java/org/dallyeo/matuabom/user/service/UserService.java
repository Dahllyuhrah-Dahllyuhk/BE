package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository userJpaRepository;
    private final MeetingRepository meetingRepository;

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
                    String oldNickname = u.getNickname();
                    u.setNickname(dto.getNickname());
                    u.setProfileImageUrl(dto.getProfileImageUrl());
                    UserEntity saved = userJpaRepository.save(u);
                    // 닉네임이 변경된 경우 Meeting 참여자 이름도 동기화
                    if (!dto.getNickname().equals(oldNickname) && saved.getMongoId() != null) {
                        syncParticipantNames(saved.getMongoId(), dto.getNickname());
                    }
                    return saved;
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

    /** 현재 약관 버전에 대한 동의를 기록한다. */
    @Transactional
    public void agreeTerms(String userId) {
        UserEntity user = findById(userId);
        user.setTermsAgreedAt(java.time.Instant.now());
        user.setTermsVersion(UserEntity.CURRENT_TERMS_VERSION);
        userJpaRepository.save(user);
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

    /**
     * 닉네임 변경 시 PENDING/CONFIRMED 모임의 참여자 이름 동기화.
     */
    private void syncParticipantNames(String userId, String newNickname) {
        List<Meeting> meetings = meetingRepository
                .findAllByHostUserIdOrParticipantsUserId(userId, userId);
        for (Meeting meeting : meetings) {
            boolean changed = false;
            for (var participant : meeting.getParticipants()) {
                if (userId.equals(participant.getUserId())
                        && !newNickname.equals(participant.getName())) {
                    participant.setName(newNickname);
                    changed = true;
                }
            }
            if (changed) meetingRepository.save(meeting);
        }
    }
}

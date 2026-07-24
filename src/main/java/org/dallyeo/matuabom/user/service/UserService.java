package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.dto.UpsertKakaoUserDto;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.domain.User;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;

    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + userId));
    }

    /**
     * 카카오 로그인/회원가입 upsert.
     * @return userId (User._id 문자열 — Meeting 등 다른 컬렉션과의 참조 키)
     */
    public String upsertKakaoUser(UpsertKakaoUserDto dto) {
        User user = userRepository.findByKakaoId(dto.getKakaoId())
                .map(u -> {
                    String oldNickname = u.getNickname();
                    u.setNickname(dto.getNickname());
                    u.setProfileImageUrl(dto.getProfileImageUrl());
                    u.setUpdatedAt(Instant.now());
                    User saved = userRepository.save(u);
                    // 닉네임이 변경된 경우 Meeting 참여자 이름도 동기화
                    if (!dto.getNickname().equals(oldNickname)) {
                        syncParticipantNames(saved.getId(), dto.getNickname());
                    }
                    return saved;
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .kakaoId(dto.getKakaoId())
                            .nickname(dto.getNickname())
                            .profileImageUrl(dto.getProfileImageUrl())
                            .build();
                    return userRepository.save(newUser);
                });

        return user.getId();
    }

    /** 현재 약관 버전에 대한 동의를 기록한다. */
    public void agreeTerms(String userId) {
        User user = findById(userId);
        user.setTermsAgreedAt(Instant.now());
        user.setTermsVersion(User.CURRENT_TERMS_VERSION);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public void linkGoogleEmail(String userId, String googleEmail) {
        User user = findById(userId);
        user.setGoogleEmail(googleEmail);
        user.setGoogleLinked(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public String getGoogleEmail(String userId) {
        return userRepository.findById(userId)
                .map(User::getGoogleEmail)
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

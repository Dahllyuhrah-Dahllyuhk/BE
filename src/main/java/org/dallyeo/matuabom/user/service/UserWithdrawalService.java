package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.calendar.repository.GoogleOAuthClientRepository;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.repository.jpa.FriendJpaRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.dallyeo.matuabom.user.repository.mongo.UserMongoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserMongoRepository userMongoRepository;
    private final UserJpaRepository userJpaRepository;
    private final FriendJpaRepository friendJpaRepository;
    private final GoogleOAuthClientRepository googleOAuthClientRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final MeetingRepository meetingRepository;
    private final TokenStore tokenStore;

    /**
     * 회원 탈퇴 처리.
     * - 호스트인 모임은 삭제
     * - 참여자인 모임은 참여자 목록에서 제거
     * - PostgreSQL: UserEntity, FriendEntity 삭제
     * - MongoDB: User, GoogleOAuthClientEntity, CalendarEventDto 삭제
     * - Redis: Refresh Token 삭제
     */
    @Transactional
    public void withdraw(String userId) {
        log.info("User withdrawal initiated: userId={}", userId);

        // 1. 모임 처리: 호스트면 삭제, 참여자면 목록에서 제거
        processUserMeetings(userId);

        // 2. 캘린더 이벤트 삭제 (MongoDB)
        calendarEventRepository.deleteByUserId(userId);

        // 3. Google OAuth 토큰 삭제 (MongoDB)
        googleOAuthClientRepository.deleteByUserId(userId);

        // 4. 친구 관계 삭제 (PostgreSQL)
        friendJpaRepository.deleteAllByUserId1OrUserId2(userId, userId);

        // 5. PostgreSQL UserEntity 삭제
        userJpaRepository.findByMongoId(userId)
                .ifPresent(userJpaRepository::delete);

        // 6. MongoDB User 삭제
        userMongoRepository.deleteById(userId);

        // 7. Redis: Refresh Token 삭제
        tokenStore.deleteRefreshToken(userId);

        log.info("User withdrawal completed: userId={}", userId);
    }

    private void processUserMeetings(String userId) {
        List<Meeting> meetings = meetingRepository
                .findAllByHostUserIdOrParticipantsUserId(userId, userId);

        for (Meeting meeting : meetings) {
            if (userId.equals(meeting.getHostUserId())) {
                // 호스트인 경우: 관련 CalendarEvent 삭제 후 모임 삭제
                calendarEventRepository.deleteByMeetingId(meeting.getId());
                meetingRepository.delete(meeting);
            } else {
                // 참여자인 경우: 참여자 목록에서 제거
                meeting.getParticipants().removeIf(p -> userId.equals(p.getUserId()));
                meetingRepository.save(meeting);
            }
        }
    }
}

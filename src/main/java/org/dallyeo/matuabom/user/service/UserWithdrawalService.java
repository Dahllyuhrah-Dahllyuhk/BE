package org.dallyeo.matuabom.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.TokenStore;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.calendar.repository.GoogleOAuthClientRepository;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.repository.FriendRepository;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final GoogleOAuthClientRepository googleOAuthClientRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final MeetingRepository meetingRepository;
    private final TokenStore tokenStore;

    /**
     * 회원 탈퇴 처리 (전부 MongoDB).
     * - 호스트인 모임은 삭제, 참여자인 모임은 참여자 목록에서 제거
     * - User, Friend, GoogleOAuthClient, CalendarEvent 삭제
     * - Refresh Token 삭제
     */
    public void withdraw(String userId) {
        log.info("User withdrawal initiated: userId={}", userId);

        // 1. 모임 처리: 호스트면 삭제, 참여자면 목록에서 제거
        processUserMeetings(userId);

        // 2. 캘린더 이벤트 삭제
        calendarEventRepository.deleteByUserId(userId);

        // 3. Google OAuth 토큰 삭제
        googleOAuthClientRepository.deleteByUserId(userId);

        // 4. 친구 관계 삭제
        friendRepository.deleteByUserId1OrUserId2(userId, userId);

        // 5. User 삭제
        userRepository.deleteById(userId);

        // 6. Refresh Token 삭제
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

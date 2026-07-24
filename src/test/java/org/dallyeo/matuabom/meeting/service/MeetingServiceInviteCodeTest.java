package org.dallyeo.matuabom.meeting.service;

import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.meeting.domain.*;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleCalendarService;
import org.dallyeo.matuabom.user.domain.User;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceInviteCodeTest {

    @Mock MeetingRepository meetingRepository;
    @Mock UserRepository userRepository;
    @Mock AvailableTimeCalculator availableTimeCalculator;
    @Mock GoogleOAuthClientService googleOAuthClientService;
    @Mock GoogleCalendarService googleCalendarService;
    @Mock CalendarEventRepository calendarEventRepository;
    @Mock EventSseService eventSseService;

    @InjectMocks MeetingService meetingService;

    private Meeting testMeeting;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id("user123")
            .nickname("테스트유저")
            .build();

        MeetingRequirement requirement = MeetingRequirement.builder()
            .dateRangeStart(Instant.now())
            .dateRangeEnd(Instant.now().plusSeconds(86400 * 7))
            .isAllDay(true)
            .timeConstraints(List.of())
            .build();

        testMeeting = Meeting.builder()
            .id("meeting123")
            .hostUserId("host456")
            .name("테스트 모임")
            .status("PENDING")
            .inviteCode("ABCD1234")
            .requirement(requirement)
            .participants(new ArrayList<>())
            .build();
    }

    @Test
    @DisplayName("유효하지 않은 초대코드로 참여 시 예외 발생")
    void joinWithInvalidCode() {
        given(meetingRepository.findByInviteCode(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.joinByInviteCode("user123", "INVALID1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("유효하지 않은 초대 코드");
    }

    @Test
    @DisplayName("CONFIRMED 모임은 참여 불가")
    void cannotJoinConfirmedMeeting() {
        testMeeting.setStatus("CONFIRMED");
        given(meetingRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(testMeeting));

        assertThatThrownBy(() -> meetingService.joinByInviteCode("user123", "ABCD1234"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("조율 중인 모임");
    }

    @Test
    @DisplayName("이미 참여한 모임에 재참여 시 예외 발생")
    void cannotJoinAlreadyJoinedMeeting() {
        MeetingParticipant existing = new MeetingParticipant();
        existing.setUserId("user123");
        testMeeting.getParticipants().add(existing);

        given(meetingRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(testMeeting));

        assertThatThrownBy(() -> meetingService.joinByInviteCode("user123", "ABCD1234"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 참여");
    }

    @Test
    @DisplayName("정상 참여 시 참여자 목록에 추가됨")
    void joinSuccessfully() {
        given(meetingRepository.findByInviteCode("ABCD1234")).willReturn(Optional.of(testMeeting));
        given(userRepository.findById("user123")).willReturn(Optional.of(testUser));
        given(availableTimeCalculator.expandDateRange(any(), any())).willReturn(List.of(java.time.LocalDate.now()));
        given(calendarEventRepository.findByUserIdInAndStartTimestampLessThanAndEndTimestampGreaterThan(any(), anyLong(), anyLong()))
            .willReturn(List.of());
        given(availableTimeCalculator.calculateFixedImpossibleSlots(any(), any(), any(), any()))
            .willReturn(List.of());
        given(meetingRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Meeting result = meetingService.joinByInviteCode("user123", "ABCD1234");

        assertThat(result.getParticipants()).hasSize(1);
        assertThat(result.getParticipants().get(0).getUserId()).isEqualTo("user123");
        assertThat(result.getParticipants().get(0).getName()).isEqualTo("테스트유저");
    }
}

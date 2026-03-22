package org.dallyeo.matuabom.meeting.service;

import org.dallyeo.matuabom.meeting.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AvailableTimeCalculatorTest {

    @Mock
    private org.dallyeo.matuabom.calendar.service.CalendarEventService calendarEventService;
    @Mock
    private org.dallyeo.matuabom.timetable.service.TimetableService timetableService;

    @InjectMocks
    private AvailableTimeCalculator calculator;

    private MeetingRequirement allDayRequirement(int days) {
        Instant start = LocalDate.now().atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Instant end = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        return MeetingRequirement.builder()
            .dateRangeStart(start)
            .dateRangeEnd(end)
            .isAllDay(true)
            .timeConstraints(Collections.emptyList())
            .build();
    }

    @Test
    @DisplayName("참여자가 없으면 공통 가용 슬롯은 빈 리스트")
    void noParticipantsReturnsEmpty() {
        List<AvailableSlot> result = calculator.findCommonAvailableSlots(Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reflectCalendar=false, reflectTimetable=false 이면 불가 슬롯 없음")
    void noReflectMeansNoImpossibleSlots() {
        MeetingParticipant participant = new MeetingParticipant();
        participant.setUserId("user1");
        participant.setReflectCalendar(false);
        participant.setReflectTimetable(false);

        List<ParticipantTimeStatus> result = calculator.calculateFixedImpossibleSlots(
            participant, allDayRequirement(3));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("날짜 범위 확장 - 3일 범위는 3개의 날짜 반환")
    void expandDateRangeReturnsCorrectCount() {
        Instant start = LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Instant end = LocalDate.of(2025, 1, 3).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();

        List<LocalDate> dates = calculator.expandDateRange(start, end);

        assertThat(dates).hasSize(3);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(dates.get(2)).isEqualTo(LocalDate.of(2025, 1, 3));
    }

    @Test
    @DisplayName("모든 참여자가 특정 시간대가 불가능하면 공통 슬롯에서 제외")
    void commonSlotExcludesImpossibleHours() {
        ParticipantTimeStatus status = new ParticipantTimeStatus(
            LocalDate.now(), new HashSet<>(Set.of(9, 10, 11)));

        MeetingParticipant p1 = new MeetingParticipant();
        p1.setUserId("user1");
        p1.setTimeStatuses(List.of(status));

        MeetingParticipant p2 = new MeetingParticipant();
        p2.setUserId("user2");
        p2.setTimeStatuses(List.of(
            new ParticipantTimeStatus(LocalDate.now(), new HashSet<>(Set.of(9, 10, 11)))));

        List<AvailableSlot> slots = calculator.findCommonAvailableSlots(List.of(p1, p2));

        // 9, 10, 11시는 모두 불가 → 해당 시간대 슬롯 없어야 함
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        boolean has9am = slots.stream().anyMatch(s -> {
            LocalTime t = s.getStart().atZone(seoul).toLocalTime();
            return t.getHour() == 9;
        });
        assertThat(has9am).isFalse();
    }
}

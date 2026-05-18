package org.dallyeo.matuabom.stats.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.stats.domain.TimeSlot;
import org.dallyeo.matuabom.stats.dto.DashboardStats;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.domain.MeetingParticipant;
import org.dallyeo.matuabom.stats.dto.TimeSlotStatDto;
import org.dallyeo.matuabom.stats.dto.TopPartnerDto;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final MeetingRepository meetingRepository;

    public DashboardStats getDashboardStats(String userId, Instant now) {
        int myThisMonthMeetingCount = getMyThisMonthMeetingCount(userId);

        // 확정된 미팅 목록
        List<Meeting> confirmedMeetings = meetingRepository.findByStatusAndParticipantsUserId("CONFIRMED", userId);
        List<Meeting> confirmedOrClosedMeetings = meetingRepository.findByStatusOrStatusAndParticipantsUserId("CONFIRMED", "CLOSED", userId);

        long upcomingCount = confirmedMeetings.stream()
                .filter(m -> m.getConfirmedStart() != null && m.getConfirmedStart().isAfter(now))
                .filter(m -> isAccepted(m, userId))
                .count();

        Map<TimeSlot, Long> count = new EnumMap<>(TimeSlot.class);
        for (TimeSlot slot : TimeSlot.values()) {
            count.put(slot, 0L);
        }

        for (Meeting meeting : confirmedOrClosedMeetings) {
            Instant start = meeting.getConfirmedStart();
            if (start == null) continue;
            // 미래에 있는 모임은 포함 x
            if (start.isAfter(now)) continue;
            TimeSlot timeSlot = toTimeSlot(start);
            count.put(timeSlot, count.get(timeSlot) + 1);
        }

        List<TimeSlotStatDto> timeSlotStats = count.entrySet().stream()
                .map(e -> new TimeSlotStatDto(e.getKey().name(), e.getValue()))
                .toList();

        String topTimeSlot = count.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().name())
                .orElse(null);

        return new DashboardStats(upcomingCount, myThisMonthMeetingCount, topTimeSlot, timeSlotStats);
    }

    public List<TopPartnerDto> getTopPartners(String userId, int limit) {
        return getTopPartners(userId, limit, Instant.now());
    }

    public List<TopPartnerDto> getTopPartners(String userId, int limit, Instant now) {
        List<Meeting> meetings = meetingRepository.findByStatusOrStatusAndParticipantsUserId("CONFIRMED", "CLOSED", userId);
        Map<String, PartnerCounter> countingMap = new HashMap<>();

        for (Meeting meeting : meetings) {
            if (meeting.getParticipants() == null || meeting.getConfirmedStart() == null) continue;
            if (meeting.getConfirmedStart().isAfter(now)) continue;
            if (!isAccepted(meeting, userId)) continue;

            for (MeetingParticipant participant : meeting.getParticipants()) {
                if (participant.getUserId().equals(userId)) continue;
                if ("ACCEPTED".equals(participant.getStatus())) {
                    countingMap.compute(participant.getUserId(), (key, value) -> {
                        if (value == null) return new PartnerCounter(participant.getUserId(), participant.getName(), 1);
                        value.count++;
                        return value;
                    });
                }
            }
        }

        return countingMap.values().stream()
                .sorted(Comparator.comparingLong(PartnerCounter::getCount).reversed())
                .limit(limit)
                .map(pc -> new TopPartnerDto(pc.getUserId(), pc.getName(), pc.getCount()))
                .toList();
    }

    public int getMyThisMonthMeetingCount(String userId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        YearMonth thisMonth = YearMonth.from(now);
        LocalDate firstDay = thisMonth.atDay(1);
        Instant monthStart = firstDay.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Instant nowInstant = now.toInstant();

        List<Meeting> myMeetings =
                meetingRepository.findAllByHostUserIdOrParticipantsUserId(userId, userId);

        long count = myMeetings.stream()
                .filter(m -> "CONFIRMED".equals(m.getStatus()) || "CLOSED".equals(m.getStatus()))
                .filter(m -> {
                    Instant start = m.getConfirmedStart();
                    return start != null
                            && !start.isBefore(monthStart)
                            && !start.isAfter(nowInstant);
                })
                .filter(m -> isAccepted(m, userId))
                .count();

        return (int) count;
    }

    private boolean isAccepted(Meeting meeting, String userId) {
        if (meeting.getParticipants() == null) return false;
        return meeting.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId) && "ACCEPTED".equals(p.getStatus()));
    }

    @Getter
    @AllArgsConstructor
    private static class PartnerCounter {
        private String userId;
        private String name;
        private long count;
    }

    private TimeSlot toTimeSlot(Instant start) {
        LocalDateTime local = LocalDateTime.ofInstant(start, ZoneId.of("Asia/Seoul"));
        int hour = local.getHour();
        if (hour < 6) return TimeSlot.DAWN;
        if (hour < 12) return TimeSlot.MORNING;
        if (hour < 18) return TimeSlot.AFTERNOON;
        return TimeSlot.EVENING;
    }
}

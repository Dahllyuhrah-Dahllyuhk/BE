package org.dallyeo.matuabom.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.TimeSlot;
import org.dallyeo.matuabom.dto.DashboardStats;
import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.dallyeo.matuabom.domain.meeting.MeetingParticipant;
import org.dallyeo.matuabom.dto.TimeSlotStatDto;
import org.dallyeo.matuabom.dto.TopPartnerDto;
import org.dallyeo.matuabom.repository.MeetingRepository;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;



@Service
@RequiredArgsConstructor
public class StatsService {
    private final MeetingRepository meetingRepository;

    public DashboardStats getDashboardStats(String userId, Instant now) {
        int myThisMonthMeetingCount = getMyThisMonthMeetingCount(userId);
        //예정된 모임 수
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

            //현시점에서 미래에 있는 모임은 포함 x
            if(start.isAfter(now)) continue;

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

        return new DashboardStats(upcomingCount, myThisMonthMeetingCount,topTimeSlot,timeSlotStats);
    }

    public List<TopPartnerDto> getTopPartners(String userId, int limit) {
        List<Meeting> meetings = meetingRepository.findByStatusOrStatusAndParticipantsUserId("CONFIRMED", "CLOSED", userId);
        Map<String, PartnerCounter> countingMap = new HashMap<>();
        for (Meeting meeting : meetings) {
            if (meeting.getParticipants() == null || meeting.getConfirmedStart().isAfter(Instant.now())) continue;

            boolean meAccepted = isAccepted(meeting, userId);

            if(!meAccepted) continue;

            for (MeetingParticipant participant : meeting.getParticipants()) {
                if (participant.getUserId().equals(userId)) continue;

                if (participant.getStatus().equals("ACCEPTED")) {
                    countingMap.compute(participant.getUserId(), (key, value) -> { //키값이 없으면 value가 null로 자동으로 되고 value count 값을 1로 초기화. 키값이 있다면 value의 count를 +1
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
                .map(pc->new TopPartnerDto(pc.getUserId(),pc.getName(),pc.getCount()))
                .toList();
    }

    public int getMyThisMonthMeetingCount(String userId) {
            // 한국 시간 기준 "지금"
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

            // 이번 달 1일 00:00
            YearMonth thisMonth = YearMonth.from(now);
            LocalDate firstDay = thisMonth.atDay(1);

            Instant monthStart = firstDay.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
            Instant nowInstant = now.toInstant();

            List<Meeting> myMeetings =
                    meetingRepository.findAllByHostUserIdOrParticipantsUserId(userId, userId);

            long count = myMeetings.stream()
                    // 확정 또는 종료된 모임만
                    .filter(m -> "CONFIRMED".equals(m.getStatus())||"CLOSED".equals(m.getStatus()))
                    // 이번 달에 시작했고, 이미 시작된 모임만 (confirmedStart ≤ now)
                    .filter(m -> {
                        Instant start = m.getConfirmedStart();
                        return start != null
                                && !start.isBefore(monthStart)   // 이번 달 1일 이상
                                && !start.isAfter(nowInstant);   // 지금 시각보다 이전/같음
                    })
                    // 내가 참가자이고 ACCEPTED인지
                    .filter(m -> isAccepted(m,userId))
                    .count();

            return (int) count;
        }

    private boolean isAccepted(Meeting meeting, String userId) {
        if(meeting.getParticipants() == null) return false;

        return meeting.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userId) && p.getStatus().equals("ACCEPTED"));
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

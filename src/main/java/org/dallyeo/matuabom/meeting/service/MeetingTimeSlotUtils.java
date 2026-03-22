package org.dallyeo.matuabom.meeting.service;

import org.dallyeo.matuabom.meeting.domain.ParticipantTimeStatus;

import java.time.*;
import java.util.*;

/**
 * 모임 시간 슬롯 관련 유틸리티.
 * 현재 AvailableTimeCalculator가 주요 로직을 담당하므로 이 클래스는 보조 역할만 합니다.
 */
public class MeetingTimeSlotUtils {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private MeetingTimeSlotUtils() {}

    /**
     * 시작/종료 Instant 구간을 KST 기준 시간 슬롯(시 단위) Set으로 변환.
     */
    public static Set<Integer> convertRangeToSlots(Instant start, Instant end) {
        Set<Integer> slots = new HashSet<>();

        ZonedDateTime zdtStart = start.atZone(ZONE_SEOUL);
        ZonedDateTime zdtEnd = end.atZone(ZONE_SEOUL);
        ZonedDateTime current = zdtStart.withMinute(0).withSecond(0).withNano(0);

        while (current.isBefore(zdtEnd)) {
            Instant slotStart = current.toInstant();
            Instant slotEnd = current.plusHours(1).toInstant();

            if (slotStart.isBefore(end) && slotEnd.isAfter(start)) {
                slots.add(current.getHour());
            }
            current = current.plusHours(1);
        }

        return slots;
    }

    /**
     * 날짜별 불가능 슬롯 Set을 ParticipantTimeStatus 리스트로 변환.
     */
    public static List<ParticipantTimeStatus> convertToTimeStatuses(
        Map<LocalDate, Set<Integer>> impossibleSlotsMap
    ) {
        List<ParticipantTimeStatus> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Set<Integer>> entry : impossibleSlotsMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(new ParticipantTimeStatus(entry.getKey(), entry.getValue()));
            }
        }
        return result;
    }
}

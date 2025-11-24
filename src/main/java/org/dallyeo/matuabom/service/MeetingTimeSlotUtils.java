package org.dallyeo.matuabom.service;

import org.dallyeo.matuabom.domain.meeting.ParticipantTimeStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 시간 범위(Instant)와 1시간 단위 슬롯 번호(Integer) 간의 변환을 담당하는 유틸리티 클래스.
 * 모든 내부 로직은 UTC(ZoneId.of("UTC"))를 기준으로 합니다.
 */
public class MeetingTimeSlotUtils {

    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");

    /**
     * Instant 범위(start, end)를 해당 날짜의 1시간 단위 슬롯 번호(0-23) 집합으로 변환합니다.
     * @param start 시간 범위 시작 Instant
     * @param end 시간 범위 끝 Instant
     * @return 슬롯 번호(0~23)의 집합
     */
    public static Set<Integer> convertRangeToSlots(Instant start, Instant end) {
        Set<Integer> slots = new HashSet<>();

        ZonedDateTime zdtStart = start.atZone(ZONE_UTC);
        ZonedDateTime zdtEnd = end.atZone(ZONE_UTC);

        LocalDate date = zdtStart.toLocalDate();

        // ZonedDateTime을 정시로 맞춥니다.
        ZonedDateTime current = zdtStart.withMinute(0).withSecond(0).withNano(0);

        // Instant 비교를 ZonedDateTime 비교로 대체 (훨씬 안정적)
        while (current.isBefore(zdtEnd)) {
            // 현재 시간이 해당 날짜에 속하는지 확인
            if (!current.toLocalDate().equals(date)) {
                // 경계 조건: 날짜가 바뀌면 해당 날짜의 슬롯은 끝
                break;
            }

            Instant slotStartInstant = current.toInstant();
            Instant slotEndInstant = current.plusHours(1).toInstant();

            // 슬롯의 시작 시간이 end Instant보다 앞서고, 슬롯의 끝 시간이 start Instant보다 뒤에 있으면 겹침
            if (slotStartInstant.isBefore(end) && slotEndInstant.isAfter(start)) {
                slots.add(current.getHour());
            }

            current = current.plusHours(1);
        }

        return slots;
    }

    /**
     * 슬롯 번호 집합을 병합하여 ParticipantTimeStatus 목록으로 변환합니다.
     * (이 함수는 슬롯 기반 로직에서 거의 사용되지 않으며, 주로 테스트 및 임시 변환용으로 남겨둡니다.)
     */
    public static List<ParticipantTimeStatus> convertSlotsToRanges(
        LocalDate date,
        Set<Integer> slots,
        String status
    ) {
        if (slots.isEmpty()) return Collections.emptyList();

        List<Integer> sortedSlots = new ArrayList<>(slots);
        Collections.sort(sortedSlots);

        List<ParticipantTimeStatus> ranges = new ArrayList<>();

        // NOTE: 이 함수는 이제 ParticipantTimeStatus 모델이 슬롯 기반이므로,
        // Instant 범위가 아닌 슬롯 목록을 반환해야 하지만,
        // 기존 시스템 호환성을 위해 Instant 범위로 변환하는 코드를 유지합니다.

        return ranges; // 실제 로직이 필요하면 구현
    }
}
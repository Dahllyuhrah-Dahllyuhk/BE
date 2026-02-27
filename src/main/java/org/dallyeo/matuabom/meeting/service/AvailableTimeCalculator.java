package org.dallyeo.matuabom.meeting.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.meeting.domain.*;
import org.dallyeo.matuabom.timetable.domain.TimetableItem;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.service.CalendarEventService;
import org.dallyeo.matuabom.timetable.service.TimetableService;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AvailableTimeCalculator {

    private final CalendarEventService calendarEventService;
    private final TimetableService timetableService;

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    // -------------------------------------------------------------------------
    // 1단계: 초기 가용 시간 계산 - calculateFixedImpossibleSlots 결과를 그대로 반환
    // -------------------------------------------------------------------------

    public List<ParticipantTimeStatus> calculateInitialSchedules(
        MeetingParticipant participant,
        MeetingRequirement requirement
    ) {
        return calculateFixedImpossibleSlots(participant, requirement);
    }

    // -------------------------------------------------------------------------
    // 2단계: 전체 참여자의 공통 가용 시간대를 AvailableSlot 리스트로 반환
    //   - 한 명이라도 불가능한 슬롯은 제외
    // -------------------------------------------------------------------------

    public List<AvailableSlot> findCommonAvailableSlots(List<MeetingParticipant> participants) {
        if (participants == null || participants.isEmpty()) {
            return Collections.emptyList();
        }

        // 날짜별·시간별 불가능 인원 집계
        Map<LocalDate, Map<Integer, Integer>> impossibleCountMap = new HashMap<>();
        for (MeetingParticipant participant : participants) {
            if (participant.getTimeStatuses() == null) continue;
            for (ParticipantTimeStatus status : participant.getTimeStatuses()) {
                if (status.getImpossibleSlots() == null) continue;
                for (int hour : status.getImpossibleSlots()) {
                    impossibleCountMap
                        .computeIfAbsent(status.getDate(), d -> new HashMap<>())
                        .merge(hour, 1, Integer::sum);
                }
            }
        }

        // 불가능 인원이 0인 슬롯만 AvailableSlot으로 변환
        List<AvailableSlot> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<Integer, Integer>> dateEntry : impossibleCountMap.entrySet()) {
            LocalDate date = dateEntry.getKey();
            for (int hour = 0; hour < 24; hour++) {
                int impossibleCount = dateEntry.getValue().getOrDefault(hour, 0);
                if (impossibleCount == 0) {
                    Instant start = date.atTime(hour, 0).atZone(ZONE_SEOUL).toInstant();
                    Instant end = (hour + 1 < 24)
                        ? date.atTime(hour + 1, 0).atZone(ZONE_SEOUL).toInstant()
                        : date.plusDays(1).atStartOfDay(ZONE_SEOUL).toInstant();
                    result.add(new AvailableSlot(start, end));
                }
            }
        }

        result.sort(Comparator.comparing(AvailableSlot::getStart));
        return mergeSlots(result);
    }

    // -------------------------------------------------------------------------
    // 시간대별 참여 가능 인원수 계산
    //   반환: Map<날짜, Map<시(0~23), 가능인원수>>
    // -------------------------------------------------------------------------

    public Map<LocalDate, Map<Integer, Integer>> calculateAvailableCountPerSlot(
        List<MeetingParticipant> participants
    ) {
        if (participants == null || participants.isEmpty()) {
            return Collections.emptyMap();
        }

        int total = participants.size();

        Map<LocalDate, Map<Integer, Integer>> impossibleCountMap = new HashMap<>();
        for (MeetingParticipant participant : participants) {
            if (participant.getTimeStatuses() == null) continue;
            for (ParticipantTimeStatus status : participant.getTimeStatuses()) {
                if (status.getImpossibleSlots() == null) continue;
                for (int hour : status.getImpossibleSlots()) {
                    impossibleCountMap
                        .computeIfAbsent(status.getDate(), d -> new HashMap<>())
                        .merge(hour, 1, Integer::sum);
                }
            }
        }

        Map<LocalDate, Map<Integer, Integer>> availableCountMap = new HashMap<>();
        for (Map.Entry<LocalDate, Map<Integer, Integer>> dateEntry : impossibleCountMap.entrySet()) {
            Map<Integer, Integer> hourMap = new HashMap<>();
            for (int hour = 0; hour < 24; hour++) {
                int impossible = dateEntry.getValue().getOrDefault(hour, 0);
                hourMap.put(hour, total - impossible);
            }
            availableCountMap.put(dateEntry.getKey(), hourMap);
        }

        return availableCountMap;
    }

    // -------------------------------------------------------------------------
    // 3단계: 고정 불가능 슬롯 계산 (캘린더 + 시간표 기반)
    // -------------------------------------------------------------------------

    public List<ParticipantTimeStatus> calculateFixedImpossibleSlots(
        MeetingParticipant participant,
        MeetingRequirement requirement
    ) {
        String userId = participant.getUserId();
        boolean reflectCalendar = participant.isReflectCalendar();
        boolean reflectTimetable = participant.isReflectTimetable();

        if (!reflectCalendar && !reflectTimetable) {
            return Collections.emptyList();
        }

        List<LocalDate> candidateDates = expandDateRange(
            requirement.getDateRangeStart(),
            requirement.getDateRangeEnd()
        );

        Map<LocalDate, Set<Integer>> impossibleSlotsMap = new HashMap<>();

        // 제약 조건(시간대 범위) 밖의 슬롯을 먼저 불가능으로 마킹
        generateConstraintSchedules(candidateDates, requirement)
            .forEach(fs -> mapFixedScheduleToSlots(fs, impossibleSlotsMap));

        if (reflectCalendar) {
            fetchAndNormalizeCalendarSchedules(userId, candidateDates)
                .forEach(fs -> mapFixedScheduleToSlots(fs, impossibleSlotsMap));
        }

        if (reflectTimetable) {
            fetchAndNormalizeTimetableSchedules(userId, candidateDates)
                .forEach(fs -> mapFixedScheduleToSlots(fs, impossibleSlotsMap));
        }

        return impossibleSlotsMap.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> new ParticipantTimeStatus(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * FixedSchedule을 LocalDate별 시간 번호 Set으로 분해
     * 모든 시간 비교는 KST 기준으로 수행
     */
    private void mapFixedScheduleToSlots(FixedSchedule schedule, Map<LocalDate, Set<Integer>> map) {
        ZonedDateTime scheduleStart = schedule.getStart().withZoneSameInstant(ZONE_SEOUL);
        ZonedDateTime scheduleEnd = schedule.getEnd().withZoneSameInstant(ZONE_SEOUL);

        LocalDate currentDate = scheduleStart.toLocalDate();
        LocalDate rangeEnd = scheduleEnd.toLocalDate();

        // 자정(00:00)에 딱 끝나는 경우 하루 전까지만 처리
        if (scheduleEnd.toLocalTime().equals(LocalTime.MIN)
            && scheduleStart.toLocalDate().isBefore(scheduleEnd.toLocalDate())) {
            rangeEnd = rangeEnd.minusDays(1);
        }

        while (!currentDate.isAfter(rangeEnd)) {
            for (int hour = 0; hour < 24; hour++) {
                ZonedDateTime slotStart = ZonedDateTime.of(currentDate, LocalTime.of(hour, 0), ZONE_SEOUL);
                ZonedDateTime slotEnd = slotStart.plusHours(1);

                if (slotEnd.isAfter(scheduleStart) && slotStart.isBefore(scheduleEnd)) {
                    map.computeIfAbsent(currentDate, k -> new HashSet<>()).add(hour);
                }
            }
            currentDate = currentDate.plusDays(1);
        }
    }

    // -------------------------------------------------------------------------
    // 보조 메서드: FixedSchedule 생성 및 조회
    // -------------------------------------------------------------------------

    public List<LocalDate> expandDateRange(Instant startInstant, Instant endInstant) {
        LocalDate start = startInstant.atZone(ZONE_SEOUL).toLocalDate();
        LocalDate end = endInstant.atZone(ZONE_SEOUL).toLocalDate();

        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            dates.add(current);
            current = current.plusDays(1);
        }
        return dates;
    }

    /**
     * 모임 시간 제약 조건(timeConstraints)을 기반으로 허용 시간 외 영역을 FixedSchedule로 생성
     * isAllDay=true이거나 제약 없으면 하루 전체를 허용 (빈 리스트 반환)
     */
    private List<FixedSchedule> generateConstraintSchedules(
        List<LocalDate> candidateDates,
        MeetingRequirement requirement
    ) {
        if (requirement.getIsAllDay()
            || requirement.getTimeConstraints() == null
            || requirement.getTimeConstraints().isEmpty()) {
            return Collections.emptyList();
        }

        List<FixedSchedule> schedules = new ArrayList<>();
        for (LocalDate date : candidateDates) {
            for (TimeRange range : requirement.getTimeConstraints()) {
                ZonedDateTime start = ZonedDateTime.of(date, range.getStartTime(), ZONE_SEOUL);
                ZonedDateTime end = ZonedDateTime.of(date, range.getEndTime(), ZONE_SEOUL);

                // 자정을 넘기는 시간 범위 처리 (예: 22:00 ~ 02:00)
                if (range.getEndTime().isBefore(range.getStartTime())
                    || range.getEndTime().equals(range.getStartTime())) {
                    end = end.plusDays(1);
                }

                schedules.add(new FixedSchedule(start, end, false));
            }
        }
        return mergeFixedSchedules(schedules);
    }

    /**
     * 캘린더 이벤트 조회 및 KST 기준 FixedSchedule 변환
     */
    private List<FixedSchedule> fetchAndNormalizeCalendarSchedules(
        String userId,
        List<LocalDate> candidateDates
    ) {
        LocalDate start = candidateDates.get(0);
        LocalDate end = candidateDates.get(candidateDates.size() - 1);

        long startTs = start.atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();
        long endTs = end.plusDays(1).atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();

        List<CalendarEventDto> events = calendarEventService.getEventsByUserId(userId, startTs, endTs);
        List<FixedSchedule> schedules = new ArrayList<>();

        for (CalendarEventDto event : events) {
            ZoneId eventZone = ZoneId.of(event.getTimeZone() != null ? event.getTimeZone() : "UTC");
            Instant startI = Instant.ofEpochMilli(event.getStartTimestamp());
            Instant endI = Instant.ofEpochMilli(event.getEndTimestamp());

            // 이벤트 시간을 KST로 변환 (모든 시간 처리 기준 통일)
            ZonedDateTime startZ = startI.atZone(eventZone).withZoneSameInstant(ZONE_SEOUL);
            ZonedDateTime endZ = endI.atZone(eventZone).withZoneSameInstant(ZONE_SEOUL);

            if (event.isAllDay()) {
                LocalDate startDate = startZ.toLocalDate();
                LocalDate endDate = endZ.toLocalDate(); // exclusive
                LocalDate cur = startDate;
                while (cur.isBefore(endDate)) {
                    if (candidateDates.contains(cur)) {
                        schedules.add(new FixedSchedule(
                            ZonedDateTime.of(cur, LocalTime.MIN, ZONE_SEOUL),
                            ZonedDateTime.of(cur, LocalTime.MIN, ZONE_SEOUL).plusDays(1),
                            true
                        ));
                    }
                    cur = cur.plusDays(1);
                }
            } else {
                if (!startZ.toLocalDate().isAfter(end)) {
                    schedules.add(new FixedSchedule(startZ, endZ, false));
                }
            }
        }

        return mergeFixedSchedules(schedules);
    }

    /**
     * 시간표 조회 및 KST 기준 FixedSchedule 변환 (UTC 변환 없이 KST로만 처리)
     */
    private List<FixedSchedule> fetchAndNormalizeTimetableSchedules(
        String userId,
        List<LocalDate> candidateDates
    ) {
        List<TimetableItem> timetableItems = timetableService.getTimetableItems(userId);
        List<FixedSchedule> fixedSchedules = new ArrayList<>();

        for (TimetableItem item : timetableItems) {
            DayOfWeek itemDayOfWeek = item.getDay();
            LocalTime itemStartTime = LocalTime.parse(item.getStartTime());
            LocalTime itemEndTime = LocalTime.parse(item.getEndTime());

            for (LocalDate date : candidateDates) {
                if (date.getDayOfWeek() == itemDayOfWeek) {
                    // KST 기준으로만 처리 (UTC 변환 불필요)
                    ZonedDateTime startZ = ZonedDateTime.of(date, itemStartTime, ZONE_SEOUL);
                    ZonedDateTime endZ = ZonedDateTime.of(date, itemEndTime, ZONE_SEOUL);

                    if (endZ.isBefore(startZ) || endZ.isEqual(startZ)) {
                        endZ = endZ.plusDays(1);
                    }

                    fixedSchedules.add(new FixedSchedule(startZ, endZ, false));
                }
            }
        }

        return mergeFixedSchedules(fixedSchedules);
    }

    // -------------------------------------------------------------------------
    // 보조 메서드: 슬롯 병합 (Merge Logic)
    // -------------------------------------------------------------------------

    private List<AvailableSlot> mergeSlots(List<AvailableSlot> slots) {
        if (slots == null || slots.size() <= 1) return slots;

        slots.sort(Comparator.comparing(AvailableSlot::getStart));

        List<AvailableSlot> merged = new ArrayList<>();
        AvailableSlot current = slots.get(0);

        for (int i = 1; i < slots.size(); i++) {
            AvailableSlot next = slots.get(i);
            if (next.getStart().isBefore(current.getEnd()) || next.getStart().equals(current.getEnd())) {
                Instant newEnd = current.getEnd().isAfter(next.getEnd()) ? current.getEnd() : next.getEnd();
                current = new AvailableSlot(current.getStart(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<FixedSchedule> mergeFixedSchedules(List<FixedSchedule> slots) {
        if (slots == null || slots.size() <= 1) return slots;

        List<AvailableSlot> availableSlots = slots.stream()
            .map(s -> new AvailableSlot(s.getStart().toInstant(), s.getEnd().toInstant()))
            .collect(Collectors.toList());

        List<AvailableSlot> mergedSlots = mergeSlots(availableSlots);

        return mergedSlots.stream()
            .map(s -> new FixedSchedule(s.getStart().atZone(ZONE_SEOUL), s.getEnd().atZone(ZONE_SEOUL), false))
            .collect(Collectors.toList());
    }
}

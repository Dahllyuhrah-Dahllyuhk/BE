package org.dallyeo.matuabom.service.meeting;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.meeting.*;
import org.dallyeo.matuabom.domain.TimetableItem;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.service.CalendarEventService;
import org.dallyeo.matuabom.service.TimetableService;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.dallyeo.matuabom.service.MeetingTimeSlotUtils.convertRangeToSlots;

@Component
@RequiredArgsConstructor
public class AvailableTimeCalculator {

    private final CalendarEventService calendarEventService;
    private final TimetableService timetableService;

    public static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId ZONE_UTC = ZoneId.of("UTC");
    private static final String ISO_LOCAL_DATE_FORMAT = "yyyy-MM-dd";

    // -------------------------------------------------------------------------
    // 📌 1단계: 초기 가용 시간 추천 (슬롯 기반 변경으로 단순화)
    // -------------------------------------------------------------------------

    public List<ParticipantTimeStatus> calculateInitialSchedules(
        MeetingParticipant participant,
        MeetingRequirement requirement
    ) {
        // 이 함수는 시스템 반영 시간을 계산하지 않습니다.
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // 📌 2단계: 최종 공통 가용 시간 도출 (슬롯 기반 변경으로 임시 처리)
    // -------------------------------------------------------------------------

    public List<AvailableSlot> findCommonAvailableSlots(List<MeetingParticipant> participants) {
        // NOTE: 이 로직은 슬롯 기반으로 전면 재작성되어야 합니다.
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // 📌 3단계: 시스템 반영 시간 계산 (IMPOSSIBLE 슬롯 계산)
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
     * FixedSchedule을 LocalDate별 슬롯 번호 Set에 추가하는 유틸리티
     */
    private void mapFixedScheduleToSlots(FixedSchedule schedule, Map<LocalDate, Set<Integer>> map) {

        // 모든 시간은 이제 KST 기준
        ZonedDateTime scheduleStart = schedule.getStart().withZoneSameInstant(ZONE_SEOUL);
        ZonedDateTime scheduleEnd = schedule.getEnd().withZoneSameInstant(ZONE_SEOUL);

        LocalDate currentDate = scheduleStart.toLocalDate();
        LocalDate rangeEnd = scheduleEnd.toLocalDate();

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
    // 🧰 유틸리티: FixedSchedule 데이터 패치 및 정규화
    // -------------------------------------------------------------------------

    public List<LocalDate> expandDateRange(Instant startInstant, Instant endInstant) {
        LocalDate start = startInstant.atZone(ZONE_SEOUL).toLocalDate();
        LocalDate end = endInstant.atZone(ZONE_SEOUL).toLocalDate();

        List<LocalDate> dates = new ArrayList<>();
        LocalDate currentDate = start;
        while (!currentDate.isAfter(end)) {
            dates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }
        return dates;
    }

    private List<FixedSchedule> generateConstraintSchedules(
        List<LocalDate> candidateDates,
        MeetingRequirement requirement
    ) {
        if (requirement.getIsAllDay() || requirement.getTimeConstraints() == null || requirement.getTimeConstraints().isEmpty()) {
             return candidateDates.stream()
                .map(date -> new FixedSchedule(
                    date.atStartOfDay(ZONE_SEOUL),
                    date.plusDays(1).atStartOfDay(ZONE_SEOUL),
                    false
                ))
                .collect(Collectors.toList());
        }

        List<FixedSchedule> schedules = new ArrayList<>();
        for (LocalDate date : candidateDates) {
            // NOTE: TimeRange 클래스가 LocalTime getStartTime()/getEndTime()을 가진다고 가정
            // for (TimeRange range : requirement.getTimeConstraints()) {
            //     ZonedDateTime start = ZonedDateTime.of(date, range.getStartTime(), ZONE_SEOUL);
            //     ZonedDateTime end = ZonedDateTime.of(date, range.getEndTime(), ZONE_SEOUL);

            //     if (range.getEndTime().isBefore(range.getStartTime()) || range.getEndTime().equals(range.getStartTime())) {
            //         end = end.plusDays(1);
            //     }

            //     schedules.add(new FixedSchedule(start, end, false));
            // }
        }
        return mergeFixedSchedules(schedules);
    }

    /**
     * 캘린더 일정을 가져오고 Normalization (종일 일정 처리 로직 보강)
     */
    private List<FixedSchedule> fetchAndNormalizeCalendarSchedules(
            String userId,
            List<LocalDate> candidateDates
    ) {

        // candidateDates → timestamp 범위 변환
        LocalDate start = candidateDates.get(0);
        LocalDate end = candidateDates.get(candidateDates.size() - 1);

        // KST 기준 timestamp (00:00~다음날 00:00)
        long startTs = start.atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();
        long endTs = end.plusDays(1).atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();

        // 🔥 달력 이벤트 조회 ( DB or Google 에서 이미 동기화됨 )
        List<CalendarEventDto> events =
                calendarEventService.getEventsByUserId(userId, startTs, endTs);

        List<FixedSchedule> schedules = new ArrayList<>();

        for (CalendarEventDto event : events) {

            ZoneId eventZone = ZoneId.of(
                    event.getTimeZone() != null ? event.getTimeZone() : "UTC"
            );

            // timestamp 기준 변환
            Instant startI = Instant.ofEpochMilli(event.getStartTimestamp());
            Instant endI = Instant.ofEpochMilli(event.getEndTimestamp());

            // → KST 변환 (슬롯 계산은 KST 기준)
            ZonedDateTime startZ = startI.atZone(eventZone).withZoneSameInstant(ZONE_SEOUL);
            ZonedDateTime endZ   = endI.atZone(eventZone).withZoneSameInstant(ZONE_SEOUL);

            // ----------------------
            // 종일 이벤트 처리
            // ----------------------
            if (event.isAllDay()) {

                LocalDate startDate = startZ.toLocalDate();
                LocalDate endDate = endZ.toLocalDate(); // exclusive

                LocalDate cur = startDate;

                while (cur.isBefore(endDate)) {
                    if (candidateDates.contains(cur)) {

                        ZonedDateTime dayStart = ZonedDateTime.of(cur, LocalTime.MIN, ZONE_SEOUL);
                        ZonedDateTime dayEnd = dayStart.plusDays(1);

                        schedules.add(new FixedSchedule(dayStart, dayEnd, true));
                    }
                    cur = cur.plusDays(1);
                }
            }

            // ----------------------
            // 시간 포함 이벤트 처리
            // ----------------------
            else {
                if (!startZ.toLocalDate().isAfter(end)) {
                    schedules.add(new FixedSchedule(startZ, endZ, false));
                }
            }
        }

        // 겹치는 일정 병합
        return mergeFixedSchedules(schedules);
    }

    /**
     * TimeTable 일정을 가져와 FixedSchedule로 변환하는 함수 (TimeTableService 사용 보정)
     */
    private List<FixedSchedule> fetchAndNormalizeTimetableSchedules(String userId, List<LocalDate> candidateDates) {
        List<TimetableItem> timetableItems = timetableService.getTimetableItems(userId);
        List<FixedSchedule> fixedSchedules = new ArrayList<>();

        for (TimetableItem item : timetableItems) {

            DayOfWeek itemDayOfWeek = item.getDay();
            java.time.LocalTime itemStartTime = java.time.LocalTime.parse(item.getStartTime());
            java.time.LocalTime itemEndTime = java.time.LocalTime.parse(item.getEndTime());

            for (LocalDate date : candidateDates) {
                if (date.getDayOfWeek() == itemDayOfWeek) {

                    ZonedDateTime startZ_KST = ZonedDateTime.of(date, itemStartTime, ZONE_SEOUL);
                    ZonedDateTime endZ_KST = ZonedDateTime.of(date, itemEndTime, ZONE_SEOUL);

                    if (endZ_KST.isBefore(startZ_KST) || endZ_KST.isEqual(startZ_KST)) {
                        endZ_KST = endZ_KST.plusDays(1);
                    }

                    ZonedDateTime startZ_UTC = startZ_KST.withZoneSameInstant(ZONE_UTC);
                    ZonedDateTime endZ_UTC = endZ_KST.withZoneSameInstant(ZONE_UTC);

                    fixedSchedules.add(new FixedSchedule(startZ_UTC, endZ_UTC, false));
                }
            }
        }

        return mergeFixedSchedules(fixedSchedules);
    }

    // -------------------------------------------------------------------------
    // 🧰 유틸리티: 시간 및 날짜 처리 (Merge Logic)
    // -------------------------------------------------------------------------

    private List<AvailableSlot> mergeSlots(List<AvailableSlot> slots) {
        if (slots == null || slots.size() <= 1) return slots;

        slots.sort(Comparator.comparing(AvailableSlot::getStart));

        List<AvailableSlot> merged = new ArrayList<>();
        AvailableSlot current = slots.get(0);

        for (int i = 1; i < slots.size(); i++) {
            AvailableSlot next = slots.get(i);

            if (next.getStart().isBefore(current.getEnd()) || next.getStart().equals(current.getEnd())) {
                Instant newEnd = current.getEnd().isAfter(next.getEnd())
                                 ? current.getEnd() : next.getEnd();
                current = new AvailableSlot(current.getStart(), newEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<AvailableSlot> intersectSlots(List<AvailableSlot> list1, List<AvailableSlot> list2) {
        List<AvailableSlot> result = new ArrayList<>();
        for (AvailableSlot slot1 : list1) {
            for (AvailableSlot slot2 : list2) {
                Instant start = slot1.getStart().isAfter(slot2.getStart()) ? slot1.getStart() : slot2.getStart();
                Instant end = slot1.getEnd().isBefore(slot2.getEnd()) ? slot1.getEnd() : slot2.getEnd();

                if (start.isBefore(end)) {
                    result.add(new AvailableSlot(start, end));
                }
            }
        }
        return result;
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
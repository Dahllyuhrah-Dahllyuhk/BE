package org.dallyeo.matuabom.timetable.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.timetable.domain.Timetable;
import org.dallyeo.matuabom.timetable.domain.TimetableItem;
import org.dallyeo.matuabom.timetable.dto.TimetableItemRequest;
import org.dallyeo.matuabom.timetable.repository.TimetableRepository;
import org.dallyeo.matuabom.global.exception.*;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;

    // ── 생성 ────────────────────────────────────────────────────────────────

    public Timetable createTimetable(String userId, String name) {
        Timetable timetable = Timetable.builder()
                .userId(userId)
                .name(name)
                .isPrimary(false)
                .build();
        return timetableRepository.save(timetable);
    }

    // ── 항목 추가 ─────────────────────────────────────────────────────────

    public Timetable addItemToTimetable(String userId, String timetableId, TimetableItemRequest dto) {
        Timetable timetable = getTimetableOwned(userId, timetableId);

        int newStart = timeToMinutes(dto.getStartTime());
        int newEnd   = timeToMinutes(dto.getEndTime());

        validateTimeRange(newStart, newEnd);
        checkOverlap(timetable.getItems(), dto.getDay(), newStart, newEnd, null);

        TimetableItem item = TimetableItem.builder()
                .id(UUID.randomUUID().toString())
                .title(dto.getTitle())
                .day(dto.getDay())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .color(dto.getColor())
                .build();

        timetable.getItems().add(item);
        timetable.setUpdatedAt(Instant.now());
        return timetableRepository.save(timetable);
    }

    // ── 조회 ─────────────────────────────────────────────────────────────

    public List<Timetable> getMyTimetables(String userId) {
        return timetableRepository.findAllByUserId(userId);
    }

    public Timetable getTimetable(String userId, String timetableId) {
        return getTimetableOwned(userId, timetableId);
    }

    /**
     * AvailableTimeCalculator용. 사용자의 모든 시간표 항목을 평탄화해 반환.
     */
    public List<TimetableItem> getTimetableItems(String userId) {
        return timetableRepository.findAllByUserId(userId).stream()
                .flatMap(t -> t.getItems().stream())
                .collect(Collectors.toList());
    }

    // ── 항목 수정 ─────────────────────────────────────────────────────────

    public Timetable updateTimetableItem(
            String userId, String timetableId, String itemId, TimetableItemRequest dto
    ) {
        Timetable timetable = getTimetableOwned(userId, timetableId);

        TimetableItem target = timetable.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.timetableItem());

        int newStart = timeToMinutes(dto.getStartTime());
        int newEnd   = timeToMinutes(dto.getEndTime());
        validateTimeRange(newStart, newEnd);
        checkOverlap(timetable.getItems(), dto.getDay(), newStart, newEnd, itemId);

        target.setTitle(dto.getTitle());
        target.setDay(dto.getDay());
        target.setStartTime(dto.getStartTime());
        target.setEndTime(dto.getEndTime());
        target.setColor(dto.getColor());

        timetable.setUpdatedAt(Instant.now());
        return timetableRepository.save(timetable);
    }

    // ── 항목 삭제 ─────────────────────────────────────────────────────────

    public Timetable deleteTimetableItem(String userId, String timetableId, String itemId) {
        Timetable timetable = getTimetableOwned(userId, timetableId);

        boolean removed = timetable.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw NotFoundException.timetableItem();

        timetable.setUpdatedAt(Instant.now());
        return timetableRepository.save(timetable);
    }

    // ── 이름 수정 ─────────────────────────────────────────────────────────

    public Timetable updateTimetableName(String userId, String timetableId, String newName) {
        Timetable timetable = getTimetableOwned(userId, timetableId);
        timetable.setName(newName);
        timetable.setUpdatedAt(Instant.now());
        return timetableRepository.save(timetable);
    }

    // ── 시간표 삭제 ────────────────────────────────────────────────────────

    public void deleteTimetable(String userId, String timetableId) {
        Timetable timetable = getTimetableOwned(userId, timetableId);
        timetableRepository.delete(timetable);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private Timetable getTimetableOwned(String userId, String timetableId) {
        Timetable t = timetableRepository.findById(timetableId)
                .orElseThrow(() -> NotFoundException.timetable());
        if (!t.getUserId().equals(userId))
            throw ForbiddenException.notResourceOwner();
        return t;
    }

    private void validateTimeRange(int startMinutes, int endMinutes) {
        if (startMinutes >= endMinutes)
            throw BadRequestException.invalidTimeRange();
    }

    private void checkOverlap(
            List<TimetableItem> items, DayOfWeek day,
            int newStart, int newEnd, String excludeId
    ) {
        for (TimetableItem item : items) {
            if (excludeId != null && item.getId().equals(excludeId)) continue;
            if (item.getDay() != day) continue;
            int s = timeToMinutes(item.getStartTime());
            int e = timeToMinutes(item.getEndTime());
            if (s < newEnd && e > newStart)
                throw BadRequestException.overlappingTimetable();
        }
    }

    private int timeToMinutes(String time) {
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
}

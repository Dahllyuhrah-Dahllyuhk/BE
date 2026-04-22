package org.dallyeo.matuabom.timetable.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.timetable.domain.TimetableEntity;
import org.dallyeo.matuabom.timetable.domain.TimetableItemEntity;
import org.dallyeo.matuabom.timetable.domain.TimetableItem;
import org.dallyeo.matuabom.timetable.dto.TimetableItemRequest;
import org.dallyeo.matuabom.timetable.repository.jpa.TimetableJpaRepository;
import org.dallyeo.matuabom.global.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableJpaRepository timetableJpaRepository;

    // ── 생성 ────────────────────────────────────────────────────────────────

    @Transactional
    public TimetableEntity createTimetable(String userId, String name) {
        TimetableEntity entity = TimetableEntity.builder()
                .userId(userId)
                .name(name)
                .isPrimary(false)
                .build();
        return timetableJpaRepository.save(entity);
    }

    // ── 항목 추가 ─────────────────────────────────────────────────────────

    @Transactional
    public TimetableEntity addItemToTimetable(String userId, Long timetableId, TimetableItemRequest dto) {
        TimetableEntity timetable = getTimetableOwned(userId, timetableId);

        int newStart = timeToMinutes(dto.getStartTime());
        int newEnd   = timeToMinutes(dto.getEndTime());

        validateTimeRange(newStart, newEnd);
        checkOverlap(timetable.getItems(), dto.getDay(), newStart, newEnd, null);

        TimetableItemEntity item = TimetableItemEntity.builder()
                .timetable(timetable)
                .title(dto.getTitle())
                .day(dto.getDay())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .color(dto.getColor())
                .build();

        timetable.getItems().add(item);
        return timetableJpaRepository.save(timetable);
    }

    // ── 조회 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TimetableEntity> getMyTimetables(String userId) {
        return timetableJpaRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public TimetableEntity getTimetable(String userId, Long timetableId) {
        return getTimetableOwned(userId, timetableId);
    }

    /**
     * AvailableTimeCalculator용.
     * TimetableItem(MongoDB 도메인)으로 변환해서 반환 — 기존 호출부 변경 없음.
     */
    @Transactional(readOnly = true)
    public List<TimetableItem> getTimetableItems(String userId) {
        return timetableJpaRepository.findAllByUserId(userId).stream()
                .flatMap(t -> t.getItems().stream())
                .map(this::toTimetableItem)
                .collect(Collectors.toList());
    }

    // ── 항목 수정 ─────────────────────────────────────────────────────────

    @Transactional
    public TimetableEntity updateTimetableItem(
            String userId, Long timetableId, Long itemId, TimetableItemRequest dto
    ) {
        TimetableEntity timetable = getTimetableOwned(userId, timetableId);

        TimetableItemEntity target = timetable.getItems().stream()
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

        return timetableJpaRepository.save(timetable);
    }

    // ── 항목 삭제 ─────────────────────────────────────────────────────────

    @Transactional
    public TimetableEntity deleteTimetableItem(String userId, Long timetableId, Long itemId) {
        TimetableEntity timetable = getTimetableOwned(userId, timetableId);

        boolean removed = timetable.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw NotFoundException.timetableItem();

        return timetableJpaRepository.save(timetable);
    }

    // ── 이름 수정 ─────────────────────────────────────────────────────────

    @Transactional
    public TimetableEntity updateTimetableName(String userId, Long timetableId, String newName) {
        TimetableEntity timetable = getTimetableOwned(userId, timetableId);
        timetable.setName(newName);
        return timetableJpaRepository.save(timetable);
    }

    // ── 시간표 삭제 ────────────────────────────────────────────────────────

    @Transactional
    public void deleteTimetable(String userId, Long timetableId) {
        TimetableEntity timetable = getTimetableOwned(userId, timetableId);
        timetableJpaRepository.delete(timetable);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────

    private TimetableEntity getTimetableOwned(String userId, Long timetableId) {
        TimetableEntity t = timetableJpaRepository.findById(timetableId)
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
            List<TimetableItemEntity> items, DayOfWeek day,
            int newStart, int newEnd, Long excludeId
    ) {
        for (TimetableItemEntity item : items) {
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

    /** JPA 엔티티 → 기존 MongoDB 도메인 객체 변환 (AvailableTimeCalculator 호환용) */
    private TimetableItem toTimetableItem(TimetableItemEntity e) {
        return TimetableItem.builder()
                .id(String.valueOf(e.getId()))
                .title(e.getTitle())
                .color(e.getColor())
                .day(e.getDay())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .build();
    }
}

package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.Timetable;
import org.dallyeo.matuabom.domain.TimetableItem;
import org.dallyeo.matuabom.dto.TimetableItemRequest;
import org.dallyeo.matuabom.repository.TimetableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetableRepository timetableRepository;

    // 1. 새 시간표 생성
    public Timetable createTimetable(String userId, String name) {
        Timetable timetable = Timetable.builder()
                .userId(userId)
                .name(name)
                .isPrimary(false) // 로직에 따라 첫 생성이면 true로 설정 가능
                .build();
        return timetableRepository.save(timetable);
    }

    // 2. 수업(일정) 추가 - 중복 검사 포함
    @Transactional
    public Timetable addItemToTimetable(String userId, String timetableId, TimetableItemRequest dto) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("시간표를 찾을 수 없습니다."));

        if (!timetable.getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        // 시간 변환 (분 단위 정수)
        int newStart = timeToMinutes(dto.getStartTime());
        int newEnd = timeToMinutes(dto.getEndTime());

        if (newStart >= newEnd) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 빨라야 합니다.");
        }

        // 중복 검사
        for (TimetableItem item : timetable.getItems()) {
            if (item.getDay() == dto.getDay()) { // 같은 요일인 경우만 검사
                int existingStart = timeToMinutes(item.getStartTime());
                int existingEnd = timeToMinutes(item.getEndTime());

                // 겹치는 조건: (기존 시작 < 새 종료) AND (기존 종료 > 새 시작)
                if (existingStart < newEnd && existingEnd > newStart) {
                    throw new IllegalArgumentException(
                        String.format("[%s] 수업과 시간이 겹칩니다.", item.getTitle())
                    );
                }
            }
        }

        // 아이템 생성 및 추가
        TimetableItem newItem = TimetableItem.builder()
                .id(UUID.randomUUID().toString())
                .title(dto.getTitle())
                .day(dto.getDay())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .color(dto.getColor())
                .build();

        timetable.getItems().add(newItem);
        return timetableRepository.save(timetable);
    }

    // 3. 내 시간표 목록 조회
    public List<Timetable> getMyTimetables(String userId) {
          return timetableRepository.findAllByUserId(userId);
    }

    // 4. 특정 시간표 상세 조회
    public Timetable getTimetable(String userId, String timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("시간표를 찾을 수 없습니다."));

        if (!timetable.getUserId().equals(userId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
        return timetable;
    }

    // 💡 5. AvailableTimeCalculator를 위한 핵심 메서드 추가 (오류 해결)
    public List<TimetableItem> getTimetableItems(String userId) {
        // 사용자의 모든 시간표를 가져와서, 모든 항목을 플랫하게 반환합니다.
        // TODO: 실제 서비스에서는 'isPrimary'가 true인 시간표만 반환하도록 수정해야 합니다.
        return timetableRepository.findAllByUserId(userId).stream()
                .flatMap(timetable -> timetable.getItems().stream())
                .collect(Collectors.toList());
    }


    // 6. 수업(아이템) 수정
    @Transactional
    public Timetable updateTimetableItem(String userId, String timetableId, String itemId, TimetableItemRequest dto) {
        Timetable timetable = getTimetable(userId, timetableId);

        // 수정할 아이템 찾기
        TimetableItem targetItem = timetable.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("수업을 찾을 수 없습니다."));

        // 시간 변환
        int newStart = timeToMinutes(dto.getStartTime());
        int newEnd = timeToMinutes(dto.getEndTime());

        if (newStart >= newEnd) {
            throw new IllegalArgumentException("종료 시간이 시작 시간보다 빨라야 합니다.");
        }

        // 중복 검사 (자기 자신은 제외!)
        for (TimetableItem item : timetable.getItems()) {
            if (item.getId().equals(itemId)) continue; // ⭐ 자기 자신은 건너뜀

            if (item.getDay() == dto.getDay()) {
                int existingStart = timeToMinutes(item.getStartTime());
                int existingEnd = timeToMinutes(item.getEndTime());

                if (existingStart < newEnd && existingEnd > newStart) {
                    throw new IllegalArgumentException("다른 수업과 시간이 겹칩니다.");
                }
            }
        }

        // 정보 업데이트
        targetItem.setTitle(dto.getTitle());
        targetItem.setDay(dto.getDay());
        targetItem.setStartTime(dto.getStartTime());
        targetItem.setEndTime(dto.getEndTime());
        targetItem.setColor(dto.getColor());

        return timetableRepository.save(timetable);
    }

    // 7. 수업(아이템) 삭제
    @Transactional
    public Timetable deleteTimetableItem(String userId, String timetableId, String itemId) {
        Timetable timetable = getTimetable(userId, timetableId);

        // 해당 아이템 제거 (removeIf 사용)
        boolean removed = timetable.getItems().removeIf(item -> item.getId().equals(itemId));

        if (!removed) {
            throw new IllegalArgumentException("삭제할 수업을 찾을 수 없습니다.");
        }

        return timetableRepository.save(timetable);
    }

    // 8. 시간표 이름 수정
    @Transactional
    public Timetable updateTimetableName(String userId, String timetableId, String newName) {
        Timetable timetable = getTimetable(userId, timetableId);

        if (!timetable.getUserId().equals(userId)) {
                    throw new IllegalStateException("권한이 없습니다.");
                }
        // 이름 변경
        timetable.setName(newName);

        return timetableRepository.save(timetable);
    }

    @Transactional
    public void deleteTimetable(String userId, String timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new IllegalArgumentException("시간표를 찾을 수 없습니다."));

        if (!timetable.getUserId().equals(userId)) {
            throw new IllegalStateException("권한이 없습니다.");
        }

        timetableRepository.delete(timetable);
    }

    // (유틸 메서드 timeToMinutes)
    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
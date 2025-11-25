package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends MongoRepository<CalendarEventDto, String> {

    // 🔥 FIX: userEmail -> userId로 변경
    void deleteByUserId(String userId);

    // 🔥 FIX: userEmail -> userId로 변경
    List<CalendarEventDto> findByUserIdOrderByStartTimestampAsc(String userId);

    // 🔥 FIX: userEmail -> userId로 변경
    List<CalendarEventDto> findByUserIdAndStartTimestampBetweenOrderByStartTimestampAsc(
            String userId, Long startTs, Long endTs
    );

    /**
     * 주(week) 단위·월(month) 단위 조회에서 "겹치는" 일정까지 포함하려면
     * [start < rangeEnd && end > rangeStart] 조건이 필요함.
     */
    // 🔥 FIX: userEmail -> userId로 변경
    List<CalendarEventDto> findByUserIdAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
            String userId, Long rangeEnd, Long rangeStart
    );

    // 🔥 FIX: userEmail -> userId로 변경
    void deleteByIdAndUserId(String id, String userId);

    /** 업데이트/삭제 시 소유자 검증용 */
    // 🔥 FIX: userEmail -> userId로 변경
    Optional<CalendarEventDto> findByIdAndUserId(String id, String userId);

    List<CalendarEventDto> findByMeetingId(String meetingId);
        void deleteByMeetingId(String meetingId);
}
package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends MongoRepository<CalendarEventDto, String> {
    void deleteByUserEmail(String userEmail);

    List<CalendarEventDto> findByUserEmailOrderByStartTimestampAsc(String userEmail);

    List<CalendarEventDto> findByUserEmailAndStartTimestampBetweenOrderByStartTimestampAsc(
            String userEmail, Long startTs, Long endTs
    );

    /**
     * 주(week) 단위·월(month) 단위 조회에서 "겹치는" 일정까지 포함하려면
     * [start < rangeEnd && end > rangeStart] 조건이 필요함.
     */
    List<CalendarEventDto> findByUserEmailAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
            String userEmail, Long rangeEnd, Long rangeStart
    );

    void deleteByIdAndUserEmail(String id, String userEmail);

    /** 업데이트/삭제 시 소유자 검증용 */
    Optional<CalendarEventDto> findByIdAndUserEmail(String id, String userEmail);
}

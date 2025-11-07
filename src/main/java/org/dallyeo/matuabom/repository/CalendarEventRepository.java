package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CalendarEventRepository extends MongoRepository<CalendarEventDto, String> {
    void deleteByUserEmail(String userEmail);
    List<CalendarEventDto> findByUserEmailAndStartTimestampBetweenOrderByStartTimestampAsc(
            String userEmail, Long startTs, Long endTs
        );
        List<CalendarEventDto> findByUserEmailOrderByStartTimestampAsc(String userEmail);
    List<CalendarEventDto> findByUserEmailAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
                String userEmail, Long rangeEnd, Long rangeStart
        );
}

package org.dallyeo.matuabom.calendar.repository;

import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends MongoRepository<CalendarEventDto, String> {

    void deleteByUserId(String userId);

    List<CalendarEventDto> findByUserIdOrderByStartTimestampAsc(String userId);

    List<CalendarEventDto> findByUserIdAndStartTimestampBetweenOrderByStartTimestampAsc(
            String userId, Long startTs, Long endTs
    );

    List<CalendarEventDto> findByUserIdAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
            String userId, Long rangeEnd, Long rangeStart
    );

    void deleteByIdAndUserId(String id, String userId);

    Optional<CalendarEventDto> findByIdAndUserId(String id, String userId);

    @Query("{ 'userId': ?0, 'title': { $regex: ?1, $options: 'i' }, 'startTimestamp': { $lt: ?2 }, 'endTimestamp': { $gt: ?3 } }")
    List<CalendarEventDto> findByUserIdAndTitleRegex(String userId, String keyword, Long viewEnd, Long viewStart);

    List<CalendarEventDto> findByMeetingId(String meetingId);

    void deleteByMeetingId(String meetingId);

    List<CalendarEventDto> findByUserIdInAndStartTimestampLessThanAndEndTimestampGreaterThan(
            List<String> userIds, Long endTs, Long startTs);
}

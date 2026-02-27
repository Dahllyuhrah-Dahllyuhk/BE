package org.dallyeo.matuabom.calendar.service;

import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleCalendarQueryService {

    private final CalendarEventRepository repository;

    public GoogleCalendarQueryService(CalendarEventRepository repository) {
        this.repository = repository;
    }

    public List<CalendarEventDto> query(String userId, Long startTs, Long endTs) {
        if (startTs != null && endTs != null) {
            return repository.findByUserIdAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
                userId, endTs, startTs
            );
        }
        return repository.findByUserIdOrderByStartTimestampAsc(userId);
    }
}

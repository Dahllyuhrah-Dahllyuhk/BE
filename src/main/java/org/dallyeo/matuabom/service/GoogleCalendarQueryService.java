package org.dallyeo.matuabom.service;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoogleCalendarQueryService {
  private final CalendarEventRepository repository;

  public GoogleCalendarQueryService(CalendarEventRepository repository) {
    this.repository = repository;
  }

  public List<CalendarEventDto> query(String userEmail, Long startTs, Long endTs) {
    if (startTs != null && endTs != null) {
          // ✅ 변경: BETWEEN → OVERLAP
          return repository.findByUserEmailAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
              userEmail, endTs, startTs
          );
        }
          return repository.findByUserEmailOrderByStartTimestampAsc(userEmail);
      }
}

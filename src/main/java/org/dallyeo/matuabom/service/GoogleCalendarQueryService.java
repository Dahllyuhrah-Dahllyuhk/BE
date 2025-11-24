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

  public List<CalendarEventDto> query(String userId, Long startTs, Long endTs) {
    if (startTs != null && endTs != null) {
          // 🔥 FIX: findByUserEmailAnd... -> findByUserIdAnd...로 변경
          return repository.findByUserIdAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
              userId, endTs, startTs
          );
        }
          // 🔥 FIX: findByUserEmail... -> findByUserId...로 변경
          return repository.findByUserIdOrderByStartTimestampAsc(userId);
      }
}
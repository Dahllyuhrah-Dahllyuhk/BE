package org.dallyeo.matuabom.repository;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CalendarEventRepository extends MongoRepository<CalendarEventDto, String> {
    List<CalendarEventDto> findByUserEmail(String userEmail);
    void deleteByUserEmail(String userEmail);
}

package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.service.CalendarEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarEventService calendarEventService;

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> getEvents(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        return ResponseEntity.ok(calendarEventService.getEvents(start, end));
    }

    @PostMapping("/events")
    public ResponseEntity<CalendarEventDto> create(@RequestBody CreateEventReq req) throws GeneralSecurityException, IOException {
        CalendarEventDto dto = calendarEventService.create(req);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<CalendarEventDto> update(
            @PathVariable String eventId,
            @RequestBody CreateEventReq req
    ) throws GeneralSecurityException, IOException {
        CalendarEventDto dto = calendarEventService.update(eventId, req);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable String eventId) throws GeneralSecurityException, IOException {
        calendarEventService.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}

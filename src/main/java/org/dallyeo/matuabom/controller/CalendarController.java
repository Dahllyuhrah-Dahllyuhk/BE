package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.service.CalendarEventService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarEventService calendarEventService;

    @GetMapping("/events")
    public List<CalendarEventDto> list() {
        return calendarEventService.listAll();
    }

    @PostMapping("/events")
    public CalendarEventDto create(@RequestBody CreateEventReq req) throws GeneralSecurityException, IOException {
        return calendarEventService.create(req);
    }

    @PutMapping("/events/{id}")
    public CalendarEventDto update(
            @PathVariable String id,
            @RequestBody CreateEventReq req
    ) throws GeneralSecurityException, IOException {
        return calendarEventService.update(id, req);
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable String id) throws GeneralSecurityException, IOException {
        calendarEventService.delete(id);
    }
}


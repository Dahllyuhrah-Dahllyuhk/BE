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
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarEventService calendarEventService;

    // ========================================================
    // 📌 전체 일정 조회 (구글 ↔ 몽고 실시간 싱크 자동 반영)
    // ========================================================
    @GetMapping("/events")
    public List<CalendarEventDto> getEvents(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) throws GeneralSecurityException, IOException {

        return calendarEventService.getEvents(start, end);
    }

    // ========================================================
    // 📌 일정 생성
    // ========================================================
    @PostMapping("/events")
    public CalendarEventDto create(@RequestBody CreateEventReq req)
            throws GeneralSecurityException, IOException {

        return calendarEventService.create(req);
    }

    // ========================================================
    // 📌 일정 수정
    // ========================================================
    @PutMapping("/events/{eventId}")
    public CalendarEventDto update(
            @PathVariable String eventId,
            @RequestBody CreateEventReq req
    ) throws GeneralSecurityException, IOException {

        return calendarEventService.update(eventId, req);
    }

    // ========================================================
    // 📌 일정 삭제
    // ========================================================
    @DeleteMapping("/events/{eventId}")
    public void delete(@PathVariable String eventId)
            throws GeneralSecurityException, IOException {

        calendarEventService.delete(eventId);
    }
}

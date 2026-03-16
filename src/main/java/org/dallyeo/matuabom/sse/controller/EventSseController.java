package org.dallyeo.matuabom.sse.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class EventSseController {

    private final EventSseService eventSseService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomPrincipal principal) {
        if (principal == null) {
            // 미인증 — 즉시 완료되는 emitter 반환
            SseEmitter empty = new SseEmitter(0L);
            empty.complete();
            return empty;
        }
        return eventSseService.subscribe(principal.getUserId());
    }
}

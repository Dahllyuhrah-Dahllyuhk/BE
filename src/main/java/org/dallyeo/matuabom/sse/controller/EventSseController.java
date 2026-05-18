package org.dallyeo.matuabom.sse.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.global.util.JwtUtil;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class EventSseController {

    private final EventSseService eventSseService;
    private final JwtUtil jwtUtil;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam(required = false) String token
    ) {
        String userId = null;

        // 1순위: SecurityContext (refresh token 쿠키로 인증된 경우)
        if (principal != null) {
            userId = principal.getUserId();
        }

        // 2순위: 쿼리 파라미터 access token (EventSource는 헤더 불가)
        if (userId == null && token != null) {
            try {
                if ("access".equals(jwtUtil.getTokenType(token))) {
                    userId = jwtUtil.validateAndGetSub(token);
                }
            } catch (Exception e) {
                // 유효하지 않은 토큰 — 미인증 처리
            }
        }

        if (userId == null) {
            SseEmitter empty = new SseEmitter(0L);
            empty.complete();
            return empty;
        }

        return eventSseService.subscribe(userId);
    }
}

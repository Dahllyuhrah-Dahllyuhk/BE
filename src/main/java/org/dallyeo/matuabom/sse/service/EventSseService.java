package org.dallyeo.matuabom.sse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class EventSseService {

    private static final long DEFAULT_TIMEOUT = 60L * 60 * 1000L;

    // userId → emitter 목록
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {}

        return emitter;
    }

    /**
     * 특정 유저에게 변경된 이벤트만 전송 — FE에서 해당 이벤트만 state 패치
     */
    public void sendEventsChanged(String userId, List<CalendarEventDto> changed, List<String> deletedIds) {
        List<SseEmitter> emitters = userEmitters.getOrDefault(userId, List.of());
        if (emitters.isEmpty()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("changed", changed);
        payload.put("deletedIds", deletedIds);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("SSE serialize error for userId={}: {}", userId, e.getMessage());
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("events-changed").data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(userId, e));
    }

    /**
     * 전체 새로고침 트리거 (full sync 시 등)
     */
    public void sendEventsUpdated(String userId) {
        List<SseEmitter> emitters = userEmitters.getOrDefault(userId, List.of());
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("events-updated").data("ok"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(userId, e));
    }

    /**
     * Google OAuth 토큰 갱신 실패 시 사용자에게 재연동 요청 알림.
     */
    public void sendGoogleReauthRequired(String userId) {
        List<SseEmitter> emitters = userEmitters.getOrDefault(userId, List.of());
        if (emitters.isEmpty()) {
            log.warn("Google reauth required for userId={} but no SSE connection", userId);
            return;
        }
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("google-reauth-required").data("ok"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(userId, e));
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = userEmitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) userEmitters.remove(userId);
        }
    }
}

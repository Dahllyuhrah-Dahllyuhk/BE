package org.dallyeo.matuabom.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EventSseService {

    // 1시간짜리 SSE 연결
    private static final long DEFAULT_TIMEOUT = 60L * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 클라이언트가 /api/sse/events 로 구독할 때 호출
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("connected"));
        } catch (IOException ignored) {
        }

        return emitter;
    }

    /**
     * BE에서 “일정이 변경되었다”는 신호를 날릴 때 호출
     */
    public void sendEventsUpdated() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("events-updated")
                        .data("ok"));
            } catch (IOException e) {
                emitter.complete();
            }
        }
    }
}

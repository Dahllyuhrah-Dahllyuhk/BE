package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository repository;
    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleCalendarQueryService googleCalendarQueryService;
    private final GoogleSyncService googleSyncService;
    private final EventSseService eventSseService;

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("no authenticated user");
        }
        return auth.getName();
    }

    // ==================================================
    // 조회
    // ==================================================
    /**
     * FE에서 /api/calendar/events?start=...&end=... 로 호출하는 메서드
     * start, end 가 null이면 전체 조회
     * start, end 가 epoch millis 문자열이면 범위 조회
     */
    public List<CalendarEventDto> getEvents(String start, String end) {
        String uid = userId();

        Long startTs = parseLongOrNull(start);
        Long endTs = parseLongOrNull(end);

        return googleCalendarQueryService.query(uid, startTs, endTs);
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================================================
    // 생성
    // ==================================================
    public CalendarEventDto create(CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 1) 로컬 DB에 먼저 저장
        CalendarEventDto saved = googleCalendarService.createLocalEvent(req);

        // 2) 구글 연동된 유저면, 구글 쪽은 비동기로 반영
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncCreateAsync(uid, req);
        }

        // 3) 🔥 FE 에게 “이벤트 변경” 알림
        eventSseService.sendEventsUpdated();

        return saved;
    }

    // ==================================================
    // 수정
    // ==================================================
    public CalendarEventDto update(String eventId, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 소유자 검증 (있으면)
        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        // 1) 로컬 DB 업데이트
        //   - GoogleCalendarService.updateLocalEvent 가 userId 는 내부에서 resolve 하므로 uid 인자 제거
        CalendarEventDto updated = googleCalendarService.updateLocalEvent(eventId, req);

        // 2) 구글 연동된 유저면 비동기로 구글 일정도 수정
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncUpdateAsync(uid, eventId, req);
        }

        // 3) 🔥 FE 알림
        eventSseService.sendEventsUpdated();

        return updated;
    }

    // ==================================================
    // 삭제
    // ==================================================
    public void delete(String eventId) throws GeneralSecurityException, IOException {

        String uid = userId();

        // 소유자 검증
        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        // 1) 로컬 DB에서 삭제
        googleCalendarService.deleteLocalEvent(eventId);

        // 2) 구글 연동된 유저면 비동기로 구글 일정도 삭제
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncDeleteAsync(uid, eventId);
        }

        // 3) 🔥 FE 알림
        eventSseService.sendEventsUpdated();
    }
}

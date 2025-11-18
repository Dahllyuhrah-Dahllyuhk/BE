package org.dallyeo.matuabom.service;

import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
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
    private final EventSseService eventSseService;

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("no authenticated user");
        }
        return auth.getName();
    }

    // ==================================================
    // 조회 (기존과 동일)
    // ==================================================
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
        CalendarEventDto saved;

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            saved = googleCalendarService.createGoogleEvent(tokens, uid, req);

        } else {
            // [B. 연동 안 된 사용자]
            saved = googleCalendarService.createLocalEvent(req);
        }

        eventSseService.sendEventsUpdated();
        return saved;
    }

    // ==================================================
    // 수정
    // ==================================================
    public CalendarEventDto update(String eventId, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        CalendarEventDto updated;

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            updated = googleCalendarService.updateGoogleEvent(tokens, uid, eventId, req);

        } else {
            // [B. 연동 안 된 사용자]
            updated = googleCalendarService.updateLocalEvent(eventId, req);
        }

        eventSseService.sendEventsUpdated();
        return updated;
    }

    // ==================================================
    // 삭제
    // ==================================================
    public void delete(String eventId) throws GeneralSecurityException, IOException {

        String uid = userId();

        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            googleCalendarService.deleteGoogleEvent(tokens, uid, eventId);

        } else {
            // [B. 연동 안 된 사용자]
            googleCalendarService.deleteLocalEvent(eventId);
        }

        eventSseService.sendEventsUpdated();
    }
}
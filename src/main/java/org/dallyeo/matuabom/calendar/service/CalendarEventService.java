package org.dallyeo.matuabom.calendar.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.dto.CreateEventReq;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository repository;
    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleSyncService googleSyncService;
    private final GoogleCalendarQueryService googleCalendarQueryService;
    private final EventSseService eventSseService;

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomPrincipal principal) {
            return principal.getUserId();
        }
        throw new IllegalStateException("no authenticated user");
    }

    public List<CalendarEventDto> getEvents(String start, String end) {
        String uid = userId();
        Long startTs = parseLongOrNull(start);
        Long endTs = parseLongOrNull(end);

        return googleCalendarQueryService.query(uid, startTs, endTs);
    }

    public CalendarEventDto create(CreateEventReq req) throws GeneralSecurityException, IOException {
        String uid = userId();
        boolean linked = googleTokens.isLinked(uid);

        CalendarEventDto saved;

        if (linked) {
            GoogleOAuthClientEntity tokens = googleTokens.getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token missing"));

            saved = googleCalendarService.createGoogleEvent(tokens, uid, req);
            googleSyncService.runIncrementalSync(uid);

        } else {
            saved = googleCalendarService.createLocalEvent(uid, req);
        }

        eventSseService.sendEventsUpdated(uid);
        return saved;
    }

    public CalendarEventDto update(String eventId, CreateEventReq req) throws GeneralSecurityException, IOException {
        String uid = userId();

        repository.findByIdAndUserId(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        boolean linked = googleTokens.isLinked(uid);
        CalendarEventDto updated;

        if (linked) {
            GoogleOAuthClientEntity tokens = googleTokens.getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token missing"));

            try {
                updated = googleCalendarService.updateGoogleEvent(tokens, uid, eventId, req);
            } catch (GoogleJsonResponseException e) {
                int code = e.getStatusCode();
                if (code == 404 || code == 410) {
                    repository.deleteById(eventId);
                    eventSseService.sendEventsUpdated(uid);
                    throw new IllegalStateException("Google event already removed");
                }
                throw e;
            }

            googleSyncService.runIncrementalSync(uid);

        } else {
            updated = googleCalendarService.updateLocalEvent(uid, eventId, req);
        }

        eventSseService.sendEventsUpdated(uid);
        return updated;
    }

    public void delete(String eventId) {
        String uid = userId();

        repository.findByIdAndUserId(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        if (googleTokens.isLinked(uid)) {

            googleTokens.getTokens(uid).ifPresent(tokens -> {
                try {
                    googleCalendarService.deleteGoogleEvent(tokens, uid, eventId);

                } catch (GoogleJsonResponseException e) {
                    int status = e.getStatusCode();
                    if (status == 404 || status == 410) {
                        System.out.println("Google event already deleted. Skip Google delete.");
                    } else {
                        throw new RuntimeException(e);
                    }
                } catch (Exception ignored) {}
            });

            repository.deleteById(eventId);
            googleSyncService.runIncrementalSync(uid);

        } else {
            googleCalendarService.deleteLocalEvent(uid, eventId);
            repository.deleteById(eventId);
        }

        eventSseService.sendEventsUpdated(uid);
    }

    private Long parseLongOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<CalendarEventDto> getEventsByKeyword(Long startTs, Long endTs, String keyword) {
        String uid = userId();
        Long queryStart = (startTs != null) ? startTs : 0L;
        Long queryEnd = (endTs != null) ? endTs : Long.MAX_VALUE;

        if (keyword == null || keyword.isBlank() || keyword.equals("None")) {
            return googleCalendarQueryService.query(uid, startTs, endTs);
        }

        return repository.findByUserIdAndTitleRegex(uid, keyword, queryEnd, queryStart);
    }

    public List<CalendarEventDto> getEventsByUserId(String uid, Long startTs, Long endTs) {
        if (startTs != null && endTs != null) {
            return repository.findByUserIdAndStartTimestampLessThanAndEndTimestampGreaterThanOrderByStartTimestampAsc(
                    uid, endTs, startTs
            );
        }

        return repository.findByUserIdOrderByStartTimestampAsc(uid);
    }
}

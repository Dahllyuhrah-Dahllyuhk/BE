package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final CalendarEventRepository repository;

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_OFFSET_DT  = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_ONLY_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /* ==============================
       유틸 함수
       ============================== */

    private boolean looksLikeDateOnly(String s) {
        return s != null && DATE_ONLY_RE.matcher(s.trim()).matches();
    }

    /** 문자열 → Instant 변환 */
    private Instant parseDate(String s, ZoneId zone) {
        if (s == null) return null;

        String t = s.trim();
        try {
            if (looksLikeDateOnly(t)) {
                LocalDate ld = LocalDate.parse(t, ISO_LOCAL_DATE);
                return ld.atStartOfDay(zone).toInstant();
            }

            try { return OffsetDateTime.parse(t, ISO_OFFSET_DT).toInstant(); }
            catch (Exception ignore) { }

            return Instant.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    /** 현재 로그인한 사용자 이메일 조회 */
    private String resolveUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DefaultOAuth2User ou) {
            Object email = ou.getAttributes().get("email");
            if (email != null) return email.toString();
        }
        return (auth != null ? auth.getName() : "unknown");
    }

    /** Google Calendar 클라이언트 생성 */
    private Calendar buildCalendarClient(OAuth2AuthorizedClient client)
            throws GeneralSecurityException, IOException {

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory   = GsonFactory.getDefaultInstance();

        return new Calendar.Builder(
                httpTransport,
                jsonFactory,
                req -> req.getHeaders().setAuthorization(
                        "Bearer " + client.getAccessToken().getTokenValue()
                )
        ).setApplicationName("Matuabom Calendar Integration").build();
    }

    /** Google Event → DAO DTO 매핑 */
    private CalendarEventDto toDto(Event event, String userEmail, ZoneId zone) {

        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        long sTs, eTs;
        String sIso, eIso;

        if (allDay) {
            sTs = event.getStart().getDate().getValue();
            eTs = event.getEnd().getDate().getValue();

            LocalDate sDate = Instant.ofEpochMilli(sTs).atZone(zone).toLocalDate();
            LocalDate eDate = Instant.ofEpochMilli(eTs).atZone(zone).toLocalDate();

            sIso = sDate.toString();
            eIso = eDate.toString();
        } else {
            sTs = event.getStart().getDateTime().getValue();
            eTs = event.getEnd().getDateTime().getValue();

            sIso = Instant.ofEpochMilli(sTs).atZone(zone).toString();
            eIso = Instant.ofEpochMilli(eTs).atZone(zone).toString();
        }

        return CalendarEventDto.builder()
                .id(event.getId())
                .userEmail(userEmail)
                .title(event.getSummary())
                .start(sIso)
                .end(eIso)
                .allDay(allDay)
                .startTimestamp(sTs)
                .endTimestamp(eTs)
                .timeZone(zone.getId())
                .build();
    }

    /** Create/Update 시 Google Event 구성 */
    private Event buildGoogleEvent(CreateEventReq req, ZoneId zone) {

        String title = (req.getTitle() == null || req.getTitle().isBlank())
                ? "(제목없음)"
                : req.getTitle();

        String tzId = (req.getTimeZone() != null && !req.getTimeZone().isBlank())
                ? req.getTimeZone()
                : zone.getId();

        boolean allDay = Boolean.TRUE.equals(req.getAllDay())
                || looksLikeDateOnly(req.getStart());

        Event ev = new Event().setSummary(title);

        if (req.getDescription() != null)
            ev.setDescription(req.getDescription());

        if (allDay) {

            LocalDate startLd = (req.getStart() != null && looksLikeDateOnly(req.getStart()))
                    ? LocalDate.parse(req.getStart(), ISO_LOCAL_DATE)
                    : LocalDate.now(zone);

            LocalDate endLd = (req.getEnd() != null && looksLikeDateOnly(req.getEnd()))
                    ? LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE)
                    : startLd.plusDays(1);

            if (!endLd.isAfter(startLd)) endLd = startLd.plusDays(1);

            ev.setStart(new EventDateTime().setDate(new DateTime(startLd.toString())));
            ev.setEnd  (new EventDateTime().setDate(new DateTime(endLd.toString())));

        } else {

            Instant s = parseDate(req.getStart(), zone);
            Instant e = parseDate(req.getEnd(), zone);
            if (s == null) s = Instant.now();
            if (e == null || !e.isAfter(s)) e = s.plus(Duration.ofHours(1));

            ev.setStart(new EventDateTime()
                    .setDateTime(new DateTime(s.toEpochMilli()))
                    .setTimeZone(tzId));

            ev.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(e.toEpochMilli()))
                    .setTimeZone(tzId));
        }

        return ev;
    }

    /* ========================================
       ✅ 전체 동기화 (구글 → DB)
       ======================================== */
    public List<CalendarEventDto> fetchAndSaveAllEvents(OAuth2AuthorizedClient client)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        String email = resolveUserEmail();
        ZoneId zone = DEFAULT_ZONE;

        DateTime minTime = new DateTime(0L);
        List<Event> all = new ArrayList<>();

        String page = null;
        do {
            Events events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setShowDeleted(false)
                    .setOrderBy("startTime")
                    .setTimeMin(minTime)
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (events.getItems() != null) all.addAll(events.getItems());
            page = events.getNextPageToken();

        } while (page != null);

        List<CalendarEventDto> dtos = all.stream()
                .map(e -> toDto(e, email, zone))
                .collect(Collectors.toList());

        repository.deleteByUserEmail(email);
        repository.saveAll(dtos);

        return dtos;
    }

    /* ========================================
       ✅ 생성
       ======================================== */
    public CalendarEventDto createEvent(OAuth2AuthorizedClient client, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        String email = resolveUserEmail();
        ZoneId zone = DEFAULT_ZONE;

        Event toCreate = buildGoogleEvent(req, zone);
        Event created  = calendar.events().insert("primary", toCreate).execute();

        CalendarEventDto dto = toDto(created, email, zone);
        repository.save(dto);

        return dto;
    }

    /* ========================================
       ✅ 수정
       ======================================== */
    public CalendarEventDto updateEvent(OAuth2AuthorizedClient client, String eventId, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        String email = resolveUserEmail();
        ZoneId zone = DEFAULT_ZONE;

        Event existing = calendar.events().get("primary", eventId).execute();
        if (existing == null)
            throw new IllegalArgumentException("event not found: " + eventId);

        // 제목/설명 업데이트
        if (req.getTitle() != null)
            existing.setSummary(req.getTitle().isBlank() ? "(제목없음)" : req.getTitle());

        if (req.getDescription() != null)
            existing.setDescription(req.getDescription());

        // 종일 여부 판단
        boolean wantAllDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                looksLikeDateOnly(req.getStart()) ||
                (existing.getStart() != null && existing.getStart().getDate() != null);

        if (wantAllDay) {
            LocalDate startLd;
            LocalDate endLd;

            if (req.getStart() != null && looksLikeDateOnly(req.getStart()))
                startLd = LocalDate.parse(req.getStart(), ISO_LOCAL_DATE);
            else if (existing.getStart().getDate() != null)
                startLd = Instant.ofEpochMilli(existing.getStart().getDate().getValue()).atZone(zone).toLocalDate();
            else
                startLd = Instant.ofEpochMilli(existing.getStart().getDateTime().getValue()).atZone(zone).toLocalDate();

            if (req.getEnd() != null && looksLikeDateOnly(req.getEnd()))
                endLd = LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE);
            else if (existing.getEnd().getDate() != null)
                endLd = Instant.ofEpochMilli(existing.getEnd().getDate().getValue()).atZone(zone).toLocalDate();
            else
                endLd = Instant.ofEpochMilli(existing.getEnd().getDateTime().getValue()).atZone(zone).toLocalDate();

            if (!endLd.isAfter(startLd)) endLd = startLd.plusDays(1);

            existing.setStart(new EventDateTime().setDate(new DateTime(startLd.toString())));
            existing.setEnd(new EventDateTime().setDate(new DateTime(endLd.toString())));

        } else {

            Instant s = (req.getStart() != null)
                    ? parseDate(req.getStart(), zone)
                    : Instant.ofEpochMilli(existing.getStart().getDateTime().getValue());

            Instant e = (req.getEnd() != null)
                    ? parseDate(req.getEnd(), zone)
                    : Instant.ofEpochMilli(existing.getEnd().getDateTime().getValue());

            if (e == null || !e.isAfter(s)) e = s.plus(Duration.ofHours(1));

            existing.setStart(new EventDateTime()
                    .setDateTime(new DateTime(s.toEpochMilli()))
                    .setTimeZone(zone.getId()));

            existing.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(e.toEpochMilli()))
                    .setTimeZone(zone.getId()));
        }

        Event updated = calendar.events().update("primary", eventId, existing).execute();
        CalendarEventDto dto = toDto(updated, email, zone);

        repository.save(dto);
        return dto;
    }

    /* ========================================
       ✅ 삭제 (410 Gone 허용)
       ======================================== */
    public void deleteEvent(OAuth2AuthorizedClient client, String eventId)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);

        try {
            calendar.events().delete("primary", eventId).execute();
        } catch (GoogleJsonResponseException gjre) {
            if (gjre.getStatusCode() != 410) { // 410 Gone 은 성공으로 간주
                throw gjre;
            }
        }

        repository.deleteById(eventId);
    }

    /* ========================================
       ✅ 조회
       ======================================== */
    public List<CalendarEventDto> listAllForUser() {
        String email = resolveUserEmail();
        return repository.findByUserEmailOrderByStartTimestampAsc(email);
    }

    public List<CalendarEventDto> listRangeForUser(Long startTs, Long endTs) {
        String email = resolveUserEmail();

        if (startTs == null || endTs == null)
            return repository.findByUserEmailOrderByStartTimestampAsc(email);

        return repository.findByUserEmailAndStartTimestampBetweenOrderByStartTimestampAsc(
                email, startTs, endTs
        );
    }
}

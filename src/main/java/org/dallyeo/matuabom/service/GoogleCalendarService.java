package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Value;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Channel;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final DateTimeFormatter ISO_OFFSET_DT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_ONLY_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Value("${app.backend-base-url:http://localhost:8080}")
    private String backendBaseUrl;

    /* ==============================
       공통 유틸
       ============================== */

    private boolean looksLikeDateOnly(String s) {
        return s != null && DATE_ONLY_RE.matcher(s.trim()).matches();
    }

    public void ensureWatchChannel(GoogleOAuthClientEntity tokens)
                throws GeneralSecurityException, IOException {

            // 이미 유효한 채널이 있으면 스킵
            if (tokens.getWatchExpiresAt() != null &&
                    tokens.getWatchExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
                return;
            }

            Calendar client = buildCalendarClient(tokens);

            Channel channel = new Channel();
            channel.setId(UUID.randomUUID().toString());
            channel.setType("web_hook");
            // 배포 환경: https://matuabom.store/api/google/webhook
            channel.setAddress(backendBaseUrl + "/api/google/webhook");
            // 나중에 webhook 에서 유저 찾기 쉽게, userId를 token에 넣어둔다
            channel.setToken(tokens.getUserId());

            com.google.api.services.calendar.Calendar.Events.Watch watch =
                    client.events().watch("primary", channel);

            Channel created = watch.execute();

            tokens.setWatchChannelId(created.getId());
            tokens.setWatchResourceId(created.getResourceId());
            if (created.getExpiration() != null) {
                tokens.setWatchExpiresAt(
                        Instant.ofEpochMilli(created.getExpiration())
                );
            }
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

            try {
                return OffsetDateTime.parse(t, ISO_OFFSET_DT).toInstant();
            } catch (Exception ignore) {}

            return Instant.parse(t);

        } catch (Exception e) {
            return null;
        }
    }

    /** 현재 로그인한 사용자(userId) */
    private String resolveUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "anonymous";
        return auth.getName();
    }

    /* ============================================================
       GoogleOAuthClientEntity 기반 클라이언트 생성
       ============================================================ */

    /** Google Calendar 클라이언트 생성 */
    private Calendar buildCalendarClient(GoogleOAuthClientEntity tokens)
            throws GeneralSecurityException, IOException {

        var http = GoogleNetHttpTransport.newTrustedTransport();
        var json = GsonFactory.getDefaultInstance();

        return new Calendar.Builder(
                http,
                json,
                req -> req.getHeaders().setAuthorization("Bearer " + tokens.getAccessToken())
        ).setApplicationName("Matuabom Calendar Integration").build();
    }

    /* ============================================================
       Google Event → DTO
       ============================================================ */

    private CalendarEventDto toDto(Event event, String userKey, ZoneId zone) {

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
                .userEmail(userKey)
                .title(event.getSummary())
                .description(event.getDescription())
                .start(sIso)
                .end(eIso)
                .allDay(allDay)
                .startTimestamp(sTs)
                .endTimestamp(eTs)
                .timeZone(zone.getId())
                .build();
    }

    /* ============================================================
       Google Event 생성 공통 로직
       ============================================================ */

    private Event buildGoogleEvent(CreateEventReq req, ZoneId zone) {

        String title = (req.getTitle() == null || req.getTitle().isBlank())
                ? "(제목없음)"
                : req.getTitle();

        String tzId = (req.getTimeZone() != null && !req.getTimeZone().isBlank())
                ? req.getTimeZone()
                : zone.getId();

        boolean allDay = Boolean.TRUE.equals(req.getAllDay()) ||
                looksLikeDateOnly(req.getStart());

        Event ev = new Event().setSummary(title);

        if (req.getDescription() != null)
            ev.setDescription(req.getDescription());

        if (allDay) {

            LocalDate s = (req.getStart() != null && looksLikeDateOnly(req.getStart()))
                    ? LocalDate.parse(req.getStart(), ISO_LOCAL_DATE)
                    : LocalDate.now(zone);

            LocalDate e = (req.getEnd() != null && looksLikeDateOnly(req.getEnd()))
                    ? LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE)
                    : s.plusDays(1);

            if (!e.isAfter(s)) e = s.plusDays(1);

            ev.setStart(new EventDateTime().setDate(new DateTime(s.toString())));
            ev.setEnd(new EventDateTime().setDate(new DateTime(e.toString())));

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

    /* ============================================================
       📌 전체 동기화 (구글 → Atlas)
       ============================================================ */

    public List<CalendarEventDto> fetchAndSaveAllEvents(
            GoogleOAuthClientEntity tokens,
            String userKey
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);

        String resolvedKey = (userKey == null || userKey.isBlank())
                ? resolveUserKey()
                : userKey;

        ZoneId zone = DEFAULT_ZONE;

        // 기존 색상 보존
        Map<String, String> previousColors = new HashMap<>();
        repository.findByUserEmailOrderByStartTimestampAsc(resolvedKey)
                .forEach(e -> {
                    if (e.getColor() != null)
                        previousColors.put(e.getId(), e.getColor());
                });

        List<Event> all = new ArrayList<>();
        String page = null;

        do {
            Events events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setShowDeleted(false)
                    .setOrderBy("startTime")
                    .setTimeMin(new DateTime(0L))
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (events.getItems() != null)
                all.addAll(events.getItems());

            page = events.getNextPageToken();

        } while (page != null);

        List<CalendarEventDto> dtos = all.stream()
                .map(e -> {
                    CalendarEventDto dto = toDto(e, resolvedKey, zone);

                    if (previousColors.containsKey(dto.getId()))
                        dto.setColor(previousColors.get(dto.getId()));

                    return dto;
                })
                .collect(Collectors.toList());

        repository.deleteByUserEmail(resolvedKey);
        repository.saveAll(dtos);

        return dtos;
    }

    /* ============================================================
       📌 구글 + DB: 생성
       ============================================================ */

    // 🔴 변경: userKey 를 파라미터로 받도록 수정
    public CalendarEventDto createGoogleEvent(
            GoogleOAuthClientEntity tokens,
            String userKey,
            CreateEventReq req
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);
        ZoneId zone = DEFAULT_ZONE;

        Event toCreate = buildGoogleEvent(req, zone);
        Event created = calendar.events()
                .insert("primary", toCreate)
                .execute();

        CalendarEventDto dto = toDto(created, userKey, zone);

        if (req.getColor() != null)
            dto.setColor(req.getColor());

        return repository.save(dto);
    }

    /* ============================================================
       📌 구글 + DB: 수정
       ============================================================ */

    // 🔴 변경: userKey 를 파라미터로 받도록 수정
    public CalendarEventDto updateGoogleEvent(
            GoogleOAuthClientEntity tokens,
            String userKey,
            String eventId,
            CreateEventReq req
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);
        ZoneId zone = DEFAULT_ZONE;

        Event existing = calendar.events()
                .get("primary", eventId)
                .execute();

        if (existing == null)
            throw new IllegalArgumentException("event not found in Google: " + eventId);

        // 제목/설명
        if (req.getTitle() != null)
            existing.setSummary(req.getTitle().isBlank() ? "(제목없음)" : req.getTitle());

        if (req.getDescription() != null)
            existing.setDescription(req.getDescription());

        // 날짜/시간 처리
        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        (req.getStart() != null && looksLikeDateOnly(req.getStart()));

        if (allDay) {

            LocalDate startLd;
            LocalDate endLd;

            if (req.getStart() != null && looksLikeDateOnly(req.getStart())) {
                startLd = LocalDate.parse(req.getStart(), ISO_LOCAL_DATE);
            } else {
                startLd = Instant.ofEpochMilli(
                        existing.getStart().getDate().getValue()
                ).atZone(zone).toLocalDate();
            }

            if (req.getEnd() != null && looksLikeDateOnly(req.getEnd())) {
                endLd = LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE);
            } else {
                endLd = Instant.ofEpochMilli(
                        existing.getEnd().getDate().getValue()
                ).atZone(zone).toLocalDate();
            }

            if (!endLd.isAfter(startLd)) endLd = startLd.plusDays(1);

            existing.setStart(new EventDateTime().setDate(new DateTime(startLd.toString())));
            existing.setEnd(new EventDateTime().setDate(new DateTime(endLd.toString())));

        } else {

            String tzId = (req.getTimeZone() != null)
                    ? req.getTimeZone()
                    : zone.getId();

            Instant s = (req.getStart() != null)
                    ? parseDate(req.getStart(), zone)
                    : Instant.ofEpochMilli(
                    Optional.ofNullable(existing.getStart().getDateTime())
                            .orElse(new DateTime(System.currentTimeMillis()))
                            .getValue()
            );

            Instant e = (req.getEnd() != null)
                    ? parseDate(req.getEnd(), zone)
                    : Instant.ofEpochMilli(
                    Optional.ofNullable(existing.getEnd().getDateTime())
                            .orElse(new DateTime(s.toEpochMilli() + Duration.ofHours(1).toMillis()))
                            .getValue()
            );

            if (!e.isAfter(s))
                e = s.plus(Duration.ofHours(1));

            existing.setStart(
                    new EventDateTime()
                            .setDateTime(new DateTime(s.toEpochMilli()))
                            .setTimeZone(tzId)
            );
            existing.setEnd(
                    new EventDateTime()
                            .setDateTime(new DateTime(e.toEpochMilli()))
                            .setTimeZone(tzId)
            );
        }

        // Google에 업데이트
        Event updated = calendar.events()
                .update("primary", eventId, existing)
                .execute();

        // DTO 변환
        CalendarEventDto dto = toDto(updated, userKey, zone);

        // 색상 처리
        if (req.getColor() != null) {
            dto.setColor(req.getColor());
        } else {
            repository.findById(eventId)
                    .map(CalendarEventDto::getColor)
                    .ifPresent(dto::setColor);
        }

        return repository.save(dto);
    }

    /* ============================================================
       📌 구글 + DB: 삭제
       ============================================================ */

    // 🔴 변경: userKey 를 파라미터로 받도록 수정
    public void deleteGoogleEvent(
            GoogleOAuthClientEntity tokens,
            String userKey,
            String eventId
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);

        // 1) 먼저 구글 쪽 삭제 시도
        try {
            calendar.events()
                    .delete("primary", eventId)
                    .execute();

        } catch (GoogleJsonResponseException e) {
            int code = e.getStatusCode();

            // 이미 삭제 / 없음 → 무시
            if (code == 404 || code == 410) {
                // no-op
            }
            // 토큰 문제 → DB만 정리, 로그는 TODO
            else if (code == 401 || code == 403) {
                // TODO: log.warn(...)
            }
            // 그 외 → 다시 던져서 500
            else {
                throw e;
            }
        }

        // 2) 우리 DB에서도 삭제 (권한 체크)
        repository.findById(eventId).ifPresent(ev -> {
            if (!Objects.equals(ev.getUserEmail(), userKey)) {
                throw new IllegalStateException("권한이 없는 일정입니다.");
            }
            repository.deleteById(eventId);
        });
    }

    /* ============================================================
       📌 로컬 전용 (DB-only)
       ============================================================ */

    public CalendarEventDto createLocalEvent(CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        looksLikeDateOnly(req.getStart());

        long sTs, eTs;
        String sIso, eIso;

        if (allDay) {
            LocalDate s = looksLikeDateOnly(req.getStart())
                    ? LocalDate.parse(req.getStart(), ISO_LOCAL_DATE)
                    : LocalDate.now(zone);

            LocalDate e = looksLikeDateOnly(req.getEnd())
                    ? LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE)
                    : s.plusDays(1);

            if (!e.isAfter(s)) e = s.plusDays(1);

            ZonedDateTime sz = s.atStartOfDay(zone);
            ZonedDateTime ez = e.atStartOfDay(zone);

            sTs = sz.toInstant().toEpochMilli();
            eTs = ez.toInstant().toEpochMilli();

            sIso = s.toString();
            eIso = e.toString();

        } else {

            Instant s = parseDate(req.getStart(), zone);
            Instant e = parseDate(req.getEnd(), zone);

            if (s == null) s = Instant.now();
            if (e == null || !e.isAfter(s)) e = s.plus(Duration.ofHours(1));

            sTs = s.toEpochMilli();
            eTs = e.toEpochMilli();

            sIso = s.atZone(zone).toString();
            eIso = e.atZone(zone).toString();
        }

        CalendarEventDto dto = CalendarEventDto.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userKey)
                .title(
                        req.getTitle() == null || req.getTitle().isBlank()
                                ? "(제목없음)"
                                : req.getTitle()
                )
                .description(req.getDescription())
                .start(sIso)
                .end(eIso)
                .allDay(allDay)
                .startTimestamp(sTs)
                .endTimestamp(eTs)
                .timeZone(zone.getId())
                .color(req.getColor())
                .build();

        return repository.save(dto);
    }

    public CalendarEventDto updateLocalEvent(String eventId, CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        CalendarEventDto existing = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId));

        if (!existing.getUserEmail().equals(userKey))
            throw new IllegalStateException("권한이 없는 일정입니다.");

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        looksLikeDateOnly(req.getStart()) ||
                        existing.isAllDay();

        long sTs, eTs;
        String sIso, eIso;

        if (allDay) {

            LocalDate s;
            LocalDate e;

            if (req.getStart() != null && looksLikeDateOnly(req.getStart())) {
                s = LocalDate.parse(req.getStart(), ISO_LOCAL_DATE);
            } else {
                s = existing.isAllDay()
                        ? LocalDate.parse(existing.getStart(), ISO_LOCAL_DATE)
                        : Instant.ofEpochMilli(existing.getStartTimestamp())
                        .atZone(zone).toLocalDate();
            }

            if (req.getEnd() != null && looksLikeDateOnly(req.getEnd())) {
                e = LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE);
            } else {
                e = existing.isAllDay()
                        ? LocalDate.parse(existing.getEnd(), ISO_LOCAL_DATE)
                        : Instant.ofEpochMilli(existing.getEndTimestamp())
                        .atZone(zone).toLocalDate();
            }

            if (!e.isAfter(s)) e = s.plusDays(1);

            ZonedDateTime sz = s.atStartOfDay(zone);
            ZonedDateTime ez = e.atStartOfDay(zone);

            sTs = sz.toInstant().toEpochMilli();
            eTs = ez.toInstant().toEpochMilli();

            sIso = s.toString();
            eIso = e.toString();

        } else {

            Instant s = req.getStart() != null
                    ? parseDate(req.getStart(), zone)
                    : Instant.ofEpochMilli(existing.getStartTimestamp());

            Instant e = req.getEnd() != null
                    ? parseDate(req.getEnd(), zone)
                    : Instant.ofEpochMilli(existing.getEndTimestamp());

            if (!e.isAfter(s))
                e = s.plus(Duration.ofHours(1));

            sTs = s.toEpochMilli();
            eTs = e.toEpochMilli();

            sIso = s.atZone(zone).toString();
            eIso = e.atZone(zone).toString();
        }

        String title = req.getTitle() != null
                ? (req.getTitle().isBlank() ? "(제목없음)" : req.getTitle())
                : existing.getTitle();

        existing.setTitle(title);

        if (req.getDescription() != null)
            existing.setDescription(req.getDescription());

        existing.setAllDay(allDay);
        existing.setStart(sIso);
        existing.setEnd(eIso);
        existing.setStartTimestamp(sTs);
        existing.setEndTimestamp(eTs);
        existing.setTimeZone(zone.getId());

        if (req.getColor() != null)
            existing.setColor(req.getColor());

        return repository.save(existing);
    }

    public void deleteLocalEvent(String eventId) {
        String userKey = resolveUserKey();

        repository.findById(eventId)
                .ifPresent(ev -> {
                    if (!ev.getUserEmail().equals(userKey))
                        throw new IllegalStateException("권한 없음");

                    repository.deleteById(eventId);
                });
    }
    public void incrementalSync(GoogleOAuthClientEntity tokens)
            throws GeneralSecurityException, IOException {

        String userKey = tokens.getUserId();
        ZoneId zone = DEFAULT_ZONE;

        if (tokens.getSyncToken() == null || tokens.getSyncToken().isBlank()) {
            // syncToken 없으면 전체 동기화 한 번
            fetchAndSaveAllEvents(tokens, userKey);
            return;
        }

        Calendar calendar = buildCalendarClient(tokens);
        Events events;

        try {
            events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setShowDeleted(true)   // 삭제 이벤트 포함
                    .setSyncToken(tokens.getSyncToken())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // 410 Gone → syncToken 만료 → 전체 재동기화
            if (e.getStatusCode() == 410) {
                fetchAndSaveAllEvents(tokens, userKey);
                return;
            }
            throw e;
        }

        if (events.getItems() != null) {
            for (Event ev : events.getItems()) {
                String eventId = ev.getId();

                // 🔥 여기 수정
                boolean deleted = "cancelled".equals(ev.getStatus());

                if (deleted) {
                    // 삭제 반영
                    repository.findById(eventId).ifPresent(dto -> {
                        if (Objects.equals(dto.getUserEmail(), userKey)) {
                            repository.deleteById(eventId);
                        }
                    });
                } else {
                    // upsert
                    CalendarEventDto dto = toDto(ev, userKey, zone);
                    // 기존 색상 유지
                    repository.findById(eventId)
                            .map(CalendarEventDto::getColor)
                            .ifPresent(dto::setColor);
                    repository.save(dto);
                }
            }
        }

        String nextSyncToken = events.getNextSyncToken();
        if (nextSyncToken != null && !nextSyncToken.isBlank()) {
            tokens.setSyncToken(nextSyncToken);
        }
    }
}

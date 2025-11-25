package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    // 🔹 토큰 자동 갱신용 서비스
    private final GoogleOAuthClientService googleOAuthClientService;

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_OFFSET_DT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_ONLY_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Value("${app.backend-base-url:http://localhost:8080}")
    private String backendBaseUrl;

    /* ==============================
       유틸
       ============================== */

    private boolean looksLikeDateOnly(String s) {
        return s != null && DATE_ONLY_RE.matcher(s.trim()).matches();
    }

    /** 문자열 → Instant 변환 (날짜 or ISO-8601) */
    private Instant parseDate(String s, ZoneId zone) {
        if (s == null) return null;
        String t = s.trim();

        try {
            // yyyy-MM-dd 형식 → 해당 날짜 00시
            if (looksLikeDateOnly(t)) {
                LocalDate ld = LocalDate.parse(t, ISO_LOCAL_DATE);
                return ld.atStartOfDay(zone).toInstant();
            }

            // OffsetDateTime (e.g. 2025-11-24T10:00:00+09:00)
            try {
                return OffsetDateTime.parse(t, ISO_OFFSET_DT).toInstant();
            } catch (Exception ignore) {
            }

            // Instant 형식
            return Instant.parse(t);

        } catch (Exception e) {
            return null;
        }
    }

    /** 현재 로그인한 사용자 userId (CustomPrincipal 기준) */
    private String resolveUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "anonymous";

        if (auth.getPrincipal() instanceof CustomPrincipal principal) {
            return principal.getUserId();
        }
        return auth.getName();
    }

    /**
     * ✅ Google Calendar 클라이언트 생성
     *  - GoogleOAuthClientService 를 통해 "유효한 최신 AccessToken"을 가져와 사용
     *  - 토큰 만료 시 여기서 자동 갱신 + DB 저장
     */
    private Calendar buildCalendarClient(GoogleOAuthClientEntity tokens)
            throws GeneralSecurityException, IOException {

        var http = GoogleNetHttpTransport.newTrustedTransport();
        var json = GsonFactory.getDefaultInstance();

        // 항상 유효한 액세스 토큰 확보 (만료 시 내부에서 refresh)
        String accessToken = googleOAuthClientService.refreshAccessTokenIfExpired(tokens.getUserId());

        logger.debug("Using Google access token for user {}: {}...",
                tokens.getUserId(),
                accessToken != null && accessToken.length() > 10
                        ? accessToken.substring(0, 10)
                        : "null");

        return new Calendar.Builder(
                http,
                json,
                req -> req.getHeaders().setAuthorization("Bearer " + accessToken)
        ).setApplicationName("Matuabom Calendar Integration").build();
    }

    /* ==============================
       Webhook watch 채널 등록
       ============================== */

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
        channel.setAddress(backendBaseUrl + "/api/google/webhook");
        channel.setToken(tokens.getUserId());

        try {
            Channel created = client.events().watch("primary", channel).execute();
            tokens.setWatchChannelId(created.getId());
            tokens.setWatchResourceId(created.getResourceId());
            if (created.getExpiration() != null) {
                tokens.setWatchExpiresAt(Instant.ofEpochMilli(created.getExpiration()));
            }
        } catch (GoogleJsonResponseException e) {
            // 로컬 HTTP 환경에서는 여기서 400 / 401 날 수 있음 → 경고만 찍고 무시
            logger.warn("Google Watch registration failed (status={}): {}",
                    e.getStatusCode(), e.getDetails() != null ? e.getDetails().toString() : e.getMessage());
        }
    }

    /* ==============================
       Google Event → DTO
       ============================== */

    private CalendarEventDto toDto(Event event, String userKey, ZoneId zone) {
        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        long sTs;
        long eTs;
        String sIso;
        String eIso;

        if (allDay) {
            sTs = event.getStart().getDate().getValue();
            eTs = event.getEnd().getDate().getValue();

            LocalDate s = Instant.ofEpochMilli(sTs).atZone(zone).toLocalDate();
            LocalDate e = Instant.ofEpochMilli(eTs).atZone(zone).toLocalDate();
            sIso = s.toString();
            eIso = e.toString();
        } else {
            sTs = event.getStart().getDateTime().getValue();
            eTs = event.getEnd().getDateTime().getValue();
            sIso = Instant.ofEpochMilli(sTs).atZone(zone).toString();
            eIso = Instant.ofEpochMilli(eTs).atZone(zone).toString();
        }

        return CalendarEventDto.builder()
                .id(event.getId())          // Google Event ID 그대로 사용
                .userId(userKey)
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

    /* ==============================
       Google Event 생성 공통 로직
       ============================== */

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

        if (req.getDescription() != null) {
            ev.setDescription(req.getDescription());
        }

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
       📌 전체 동기화 (구글 → Mongo)  — runInitialSync에서 사용
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

        // 1) syncToken 확보 (singleEvents=false)
        String page = null;
        String lastSyncToken = null;

        do {
            Events evPage = calendar.events()
                    .list("primary")
                    .setSingleEvents(false)
                    .setShowDeleted(true)
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (evPage.getNextSyncToken() != null && !evPage.getNextSyncToken().isBlank()) {
                lastSyncToken = evPage.getNextSyncToken();
            }
            page = evPage.getNextPageToken();
        } while (page != null);

        if (lastSyncToken != null && !lastSyncToken.isBlank()) {
            tokens.setSyncToken(lastSyncToken);
        } else {
            logger.warn("No nextSyncToken obtained for user {}", tokens.getUserId());
        }

        // 2) UI용 전체 이벤트 수집 (singleEvents=true)
        List<Event> allExpanded = new ArrayList<>();
        page = null;

        do {
            Events evPage = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setShowDeleted(false)
                    .setOrderBy("startTime")
                    .setTimeMin(new DateTime(0L))
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (evPage.getItems() != null)
                allExpanded.addAll(evPage.getItems());

            page = evPage.getNextPageToken();
        } while (page != null);

        // 이전 색상 보존
        Map<String, String> previousColors = new HashMap<>();
        repository.findByUserIdOrderByStartTimestampAsc(resolvedKey)
                .forEach(e -> {
                    if (e.getColor() != null) {
                        previousColors.put(e.getId(), e.getColor());
                    }
                });

        // 변환 + 색상 적용
        List<CalendarEventDto> dtos = allExpanded.stream()
                .map(e -> {
                    CalendarEventDto dto = toDto(e, resolvedKey, zone);
                    if (previousColors.containsKey(dto.getId())) {
                        dto.setColor(previousColors.get(dto.getId()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // 기존 데이터 삭제 후 새 데이터 저장
        repository.deleteByUserId(resolvedKey);
        repository.saveAll(dtos);

        return dtos;
    }

    /* ============================================================
       📌 구글 + DB: 생성 / 수정 / 삭제
       ============================================================ */

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
        if (req.getColor() != null) {
            dto.setColor(req.getColor());
        }

        return repository.save(dto);
    }

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

        if (existing == null) {
            throw new IllegalArgumentException("event not found in Google: " + eventId);
        }

        // 제목/설명
        if (req.getTitle() != null) {
            existing.setSummary(req.getTitle().isBlank() ? "(제목없음)" : req.getTitle());
        }
        if (req.getDescription() != null) {
            existing.setDescription(req.getDescription());
        }

        // 날짜/시간
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
            String tzId = (req.getTimeZone() != null && !req.getTimeZone().isBlank())
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

            if (!e.isAfter(s)) e = s.plus(Duration.ofHours(1));

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

        Event updated = calendar.events()
                .update("primary", eventId, existing)
                .execute();

        CalendarEventDto dto = toDto(updated, userKey, zone);

        // 기존 색상 유지 or 새 색상 반영
        if (req.getColor() != null) {
            dto.setColor(req.getColor());
        } else {
            repository.findById(eventId)
                    .map(CalendarEventDto::getColor)
                    .ifPresent(dto::setColor);
        }

        return repository.save(dto);
    }

    public void deleteGoogleEvent(
            GoogleOAuthClientEntity tokens,
            String userKey,
            String eventId
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);

        try {
            calendar.events()
                    .delete("primary", eventId)
                    .execute();

        } catch (GoogleJsonResponseException e) {
            int code = e.getStatusCode();
            if (code != 404 && code != 410) {
                // 404/410은 이미 삭제된 상태이므로 무시, 나머지는 그대로 던짐
                throw e;
            }
        }

        repository.findById(eventId).ifPresent(ev -> {
            if (!Objects.equals(ev.getUserId(), userKey)) {
                throw new IllegalStateException("권한이 없는 일정입니다.");
            }
            repository.deleteById(eventId);
        });
    }

    /* ============================================================
       📌 로컬 전용 (카카오 로그인만 한 유저 등)
       ============================================================ */

    public CalendarEventDto createLocalEvent(String uid, CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        looksLikeDateOnly(req.getStart());

        long sTs;
        long eTs;
        String sIso;
        String eIso;

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
                .userId(uid)
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

    public CalendarEventDto updateLocalEvent(String uid, String eventId, CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;

        CalendarEventDto existing = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId));

        if (!existing.getUserId().equals(uid)) {
            throw new IllegalStateException("권한이 없는 일정입니다.");
        }

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        looksLikeDateOnly(req.getStart()) ||
                        existing.isAllDay();

        long sTs;
        long eTs;
        String sIso;
        String eIso;

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
        if (req.getDescription() != null) {
            existing.setDescription(req.getDescription());
        }
        existing.setAllDay(allDay);
        existing.setStart(sIso);
        existing.setEnd(eIso);
        existing.setStartTimestamp(sTs);
        existing.setEndTimestamp(eTs);
        existing.setTimeZone(zone.getId());
        if (req.getColor() != null) {
            existing.setColor(req.getColor());
        }

        return repository.save(existing);
    }

    public void deleteLocalEvent(String uid, String eventId) {

        repository.findById(eventId)
                .ifPresent(ev -> {
                    if (!ev.getUserId().equals(uid)) {
                        throw new IllegalStateException("권한 없음");
                    }
                    repository.deleteById(eventId);
                });
    }

    /* ============================================================
       📌 증분 동기화 (구글 → Mongo)
       ============================================================ */

    public void incrementalSync(GoogleOAuthClientEntity tokens)
            throws GeneralSecurityException, IOException {

        String userKey = tokens.getUserId();
        ZoneId zone = DEFAULT_ZONE;

        if (tokens.getSyncToken() == null || tokens.getSyncToken().isBlank()) {
            // syncToken 없으면 전체 동기화
            fetchAndSaveAllEvents(tokens, userKey);
            return;
        }

        Calendar calendar = buildCalendarClient(tokens);
        Events events;

        try {
            events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setShowDeleted(true)
                    .setSyncToken(tokens.getSyncToken())
                    .execute();
        } catch (GoogleJsonResponseException e) {
            // syncToken 만료 → full sync
            if (e.getStatusCode() == 410) {
                tokens.setSyncToken(null);
                fetchAndSaveAllEvents(tokens, userKey);
                return;
            }
            throw e;
        }

        if (events.getItems() != null) {
            for (Event ev : events.getItems()) {
                String eventId = ev.getId();
                boolean deleted = "cancelled".equals(ev.getStatus());

                if (deleted) {
                    repository.findById(eventId).ifPresent(dto -> {
                        if (Objects.equals(dto.getUserId(), userKey)) {
                            repository.delete(dto);
                        }
                    });
                } else {
                    CalendarEventDto dtoFromGoogle = toDto(ev, userKey, zone);

                    repository.findById(eventId).ifPresentOrElse(existing -> {
                        // 기존 색상 유지
                        dtoFromGoogle.setColor(existing.getColor());
                        dtoFromGoogle.setId(existing.getId());
                        repository.save(dtoFromGoogle);
                    }, () -> {
                        dtoFromGoogle.setId(eventId);
                        repository.save(dtoFromGoogle);
                    });
                }
            }
        }

        String nextSyncToken = events.getNextSyncToken();
        if (nextSyncToken != null && !nextSyncToken.isBlank()) {
            tokens.setSyncToken(nextSyncToken);
        }
    }
}

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
       공통 유틸
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

    /** 현재 로그인한 사용자 키 (JWT subject 등) */
    private String resolveUserKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "anonymous";
        return auth.getName();  // JwtAuthFilter에서 넣어준 userId
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

    /** Google Event → DTO 매핑 (색상은 여기서 바로 넣지 않고, 동기화 단계에서 덮어씀) */
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
                .userEmail(userKey) // 🔸 Atlas에서도 userKey 로 구분
                .title(event.getSummary())
                .description(event.getDescription())
                .start(sIso)
                .end(eIso)
                .allDay(allDay)
                .startTimestamp(sTs)
                .endTimestamp(eTs)
                .timeZone(zone.getId())
                // color 는 여기서 설정하지 않음 (동기화 시 기존 DB 색상을 유지하기 위해)
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
       ✅ 전체 동기화 (구글 → Atlas)
       - JwtLoginSuccessHandler 에서 구글 로그인 성공 시 호출
       - 기존 DB에 있던 색상(color)을 최대한 유지
       ======================================== */
    public List<CalendarEventDto> fetchAndSaveAllEvents(
            OAuth2AuthorizedClient client,
            String userKey    // ✅ JWT subject (우리 userId)를 넘겨받는다
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);

        // 혹시라도 null/빈 문자열이면 SecurityContext에서 한 번 더 시도
        String resolvedKey = (userKey == null || userKey.isBlank())
                    ? resolveUserKey()
                    : userKey;

        ZoneId zone = DEFAULT_ZONE;

        // ✅ 동기화 전에 기존 색상들을 보관 (eventId → color)
        Map<String, String> previousColors = new HashMap<>();
        repository.findByUserEmailOrderByStartTimestampAsc(resolvedKey)
                .forEach(e -> {
                    if (e.getColor() != null && !e.getColor().isBlank()) {
                        previousColors.put(e.getId(), e.getColor());
                    }
                });

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

            if (events.getItems() != null) {
                all.addAll(events.getItems());
            }
            page = events.getNextPageToken();

        } while (page != null);

        List<CalendarEventDto> dtos = all.stream()
                .map(e -> {
                    CalendarEventDto dto = toDto(e, resolvedKey, zone);
                    // ✅ DB에 색상이 있으면 그대로 유지
                    String prevColor = previousColors.get(dto.getId());
                    if (prevColor != null && !prevColor.isBlank()) {
                        dto.setColor(prevColor);
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // 이 userKey(=우리 userId)에 해당하는 기존 일정 싹 지우고 새로 저장
        repository.deleteByUserEmail(resolvedKey);
        repository.saveAll(dtos);

        return dtos;
    }

    /* ========================================
       ✅ Google + Atlas: 생성
       ======================================== */
    public CalendarEventDto createGoogleEvent(
            OAuth2AuthorizedClient client,
            CreateEventReq req
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        // 1) Google Event 생성
        Event toCreate = buildGoogleEvent(req, zone);
        Event created  = calendar.events()
                .insert("primary", toCreate)
                .execute();

        // 2) Google Event → DTO 변환
        CalendarEventDto dto = toDto(created, userKey, zone);

        // 3) 색상은 우리 쪽에서 관리 (CreateEventReq.color 사용)
        if (req.getColor() != null && !req.getColor().isBlank()) {
            dto.setColor(req.getColor());
        }

        // 4) DB에 저장
        return repository.save(dto);
    }

    /* ========================================
       ✅ Google + Atlas: 수정
       ======================================== */
    public CalendarEventDto updateGoogleEvent(
            OAuth2AuthorizedClient client,
            String eventId,
            CreateEventReq req
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        // 1) Google에서 기존 이벤트 조회
        Event existing = calendar.events()
                .get("primary", eventId)
                .execute();

        if (existing == null) {
            throw new IllegalArgumentException("event not found in Google: " + eventId);
        }

        // 2) 제목/설명 수정
        if (req.getTitle() != null) {
            existing.setSummary(
                    req.getTitle().isBlank() ? "(제목없음)" : req.getTitle()
            );
        }
        if (req.getDescription() != null) {
            existing.setDescription(req.getDescription());
        }

        // 3) 시간/날짜 수정
        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                (req.getStart() != null && looksLikeDateOnly(req.getStart()));

        if (allDay) {
            LocalDate startLd;
            LocalDate endLd;

            if (req.getStart() != null && looksLikeDateOnly(req.getStart())) {
                startLd = LocalDate.parse(req.getStart(), ISO_LOCAL_DATE);
            } else if (existing.getStart() != null && existing.getStart().getDate() != null) {
                DateTime existingStart = existing.getStart().getDate();
                startLd = Instant.ofEpochMilli(existingStart.getValue())
                        .atZone(zone).toLocalDate();
            } else {
                startLd = LocalDate.now(zone);
            }

            if (req.getEnd() != null && looksLikeDateOnly(req.getEnd())) {
                endLd = LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE);
            } else if (existing.getEnd() != null && existing.getEnd().getDate() != null) {
                DateTime existingEnd = existing.getEnd().getDate();
                endLd = Instant.ofEpochMilli(existingEnd.getValue())
                        .atZone(zone).toLocalDate();
            } else {
                endLd = startLd.plusDays(1);
            }

            if (!endLd.isAfter(startLd)) {
                endLd = startLd.plusDays(1);
            }

            existing.setStart(new EventDateTime().setDate(new DateTime(startLd.toString())));
            existing.setEnd  (new EventDateTime().setDate(new DateTime(endLd.toString())));

        } else {
            String tzId = (req.getTimeZone() != null && !req.getTimeZone().isBlank())
                    ? req.getTimeZone()
                    : DEFAULT_ZONE.getId();

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

            if (e == null || !e.isAfter(s)) {
                e = s.plus(Duration.ofHours(1));
            }

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

        // 4) Google에 업데이트 요청
        Event updated = calendar.events()
                .update("primary", eventId, existing)
                .execute();

        // 5) DTO로 변환 + 색상 처리
        CalendarEventDto dto = toDto(updated, userKey, zone);

        // 요청에 색이 있으면 그걸로, 없으면 기존 DB 색 유지
        if (req.getColor() != null) {
            dto.setColor(req.getColor());
        } else {
            repository.findById(eventId)
                    .map(CalendarEventDto::getColor)
                    .ifPresent(dto::setColor);
        }

        // 6) DB 저장
        return repository.save(dto);
    }

    /* ========================================
       ✅ Google + Atlas: 삭제
       ======================================== */
    public void deleteGoogleEvent(
            OAuth2AuthorizedClient client,
            String eventId
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(client);
        String userKey = resolveUserKey();

        // 1) Google에서 삭제
        calendar.events()
                .delete("primary", eventId)
                .execute();

        // 2) DB에서도 삭제 (권한 체크)
        repository.findById(eventId).ifPresent(ev -> {
            if (!Objects.equals(ev.getUserEmail(), userKey)) {
                throw new IllegalStateException("권한이 없는 일정입니다.");
            }
            repository.deleteById(eventId);
        });
    }

    /* ========================================
       ✅ 로컬 전용 생성 (Atlas)
       - 프론트에서 color도 함께 보내면 그대로 저장
       - (현재 컨트롤러에서는 사용하지 않지만, 남겨 둠)
       ======================================== */
    public CalendarEventDto createLocalEvent(CreateEventReq req) {
        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                looksLikeDateOnly(req.getStart());

        long sTs;
        long eTs;
        String sIso;
        String eIso;

        if (allDay) {
            LocalDate startLd = (req.getStart() != null && looksLikeDateOnly(req.getStart()))
                    ? LocalDate.parse(req.getStart(), ISO_LOCAL_DATE)
                    : LocalDate.now(zone);
            LocalDate endLd = (req.getEnd() != null && looksLikeDateOnly(req.getEnd()))
                    ? LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE)
                    : startLd.plusDays(1);
            if (!endLd.isAfter(startLd)) endLd = startLd.plusDays(1);

            ZonedDateTime sZdt = startLd.atStartOfDay(zone);
            ZonedDateTime eZdt = endLd.atStartOfDay(zone);

            sTs = sZdt.toInstant().toEpochMilli();
            eTs = eZdt.toInstant().toEpochMilli();
            sIso = startLd.toString();
            eIso = endLd.toString();
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

        String title = (req.getTitle() == null || req.getTitle().isBlank())
                ? "(제목없음)"
                : req.getTitle();

        CalendarEventDto dto = CalendarEventDto.builder()
                .id(UUID.randomUUID().toString())
                .userEmail(userKey)         // 🔸 Atlas 에서도 userKey 로 파티셔닝
                .title(title)
                .description(req.getDescription())
                .start(sIso)
                .end(eIso)
                .allDay(allDay)
                .startTimestamp(sTs)
                .endTimestamp(eTs)
                .timeZone(zone.getId())
                .color(req.getColor())      // ✅ 프론트에서 넘어온 색상 저장
                .build();

        return repository.save(dto);
    }

    /* ========================================
       ✅ 로컬 전용 수정 (Atlas)
       - (현재 컨트롤러에서는 사용하지 않지만, 남겨 둠)
       ======================================== */
    public CalendarEventDto updateLocalEvent(String eventId, CreateEventReq req) {
        ZoneId zone = DEFAULT_ZONE;
        String userKey = resolveUserKey();

        CalendarEventDto existing = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId));

        if (!Objects.equals(existing.getUserEmail(), userKey)) {
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
            LocalDate startLd;
            LocalDate endLd;

            if (req.getStart() != null && looksLikeDateOnly(req.getStart())) {
                startLd = LocalDate.parse(req.getStart(), ISO_LOCAL_DATE);
            } else if (existing.isAllDay() && looksLikeDateOnly(existing.getStart())) {
                startLd = LocalDate.parse(existing.getStart(), ISO_LOCAL_DATE);
            } else {
                startLd = Instant.ofEpochMilli(existing.getStartTimestamp())
                        .atZone(zone).toLocalDate();
            }

            if (req.getEnd() != null && looksLikeDateOnly(req.getEnd())) {
                endLd = LocalDate.parse(req.getEnd(), ISO_LOCAL_DATE);
            } else if (existing.isAllDay() && looksLikeDateOnly(existing.getEnd())) {
                endLd = LocalDate.parse(existing.getEnd(), ISO_LOCAL_DATE);
            } else {
                endLd = Instant.ofEpochMilli(existing.getEndTimestamp())
                        .atZone(zone).toLocalDate();
            }

            if (!endLd.isAfter(startLd)) endLd = startLd.plusDays(1);

            ZonedDateTime sZdt = startLd.atStartOfDay(zone);
            ZonedDateTime eZdt = endLd.atStartOfDay(zone);

            sTs = sZdt.toInstant().toEpochMilli();
            eTs = eZdt.toInstant().toEpochMilli();
            sIso = startLd.toString();
            eIso = endLd.toString();
        } else {
            Instant s = (req.getStart() != null)
                    ? parseDate(req.getStart(), zone)
                    : Instant.ofEpochMilli(existing.getStartTimestamp());
            Instant e = (req.getEnd() != null)
                    ? parseDate(req.getEnd(), zone)
                    : Instant.ofEpochMilli(existing.getEndTimestamp());

            if (e == null || !e.isAfter(s)) e = s.plus(Duration.ofHours(1));

            sTs = s.toEpochMilli();
            eTs = e.toEpochMilli();
            sIso = s.atZone(zone).toString();
            eIso = e.atZone(zone).toString();
        }

        String title = (req.getTitle() != null)
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

        // ✅ 색상도 함께 업데이트 (null이면 건드리지 않음)
        if (req.getColor() != null) {
            existing.setColor(req.getColor());
        }

        return repository.save(existing);
    }

    /* ========================================
       ✅ 로컬 전용 삭제 (Atlas)
       ======================================== */
    public void deleteLocalEvent(String eventId) {
        String userKey = resolveUserKey();
        repository.findById(eventId).ifPresent(ev -> {
            if (!Objects.equals(ev.getUserEmail(), userKey)) {
                throw new IllegalStateException("권한이 없는 일정입니다.");
            }
            repository.deleteById(eventId);
        });
    }

    /* ========================================
       ✅ 조회 (Atlas)
       ======================================== */
    public List<CalendarEventDto> listAllForUser() {
        String key = resolveUserKey();
        return repository.findByUserEmailOrderByStartTimestampAsc(key);
    }

    public List<CalendarEventDto> listRangeForUser(Long startTs, Long endTs) {
        String key = resolveUserKey();
        if (startTs == null || endTs == null) {
            return repository.findByUserEmailOrderByStartTimestampAsc(key);
        }
        return repository
                .findByUserEmailAndStartTimestampBetweenOrderByStartTimestampAsc(key, startTs, endTs);
    }
}

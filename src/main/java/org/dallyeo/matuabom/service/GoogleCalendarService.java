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
    private final UserService userService;   // ✅ 멀티 유저용

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

            try {
                return OffsetDateTime.parse(t, ISO_OFFSET_DT).toInstant();
            } catch (Exception ignore) { }

            return Instant.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ✅ 현재 JWT 로 인증된 카카오 유저 → UserService 를 통해 구글 이메일 조회
     *    required=true  : 구글 이메일 없으면 예외 (동기화/쓰기 작업에 사용)
     *    required=false : 구글 이메일 없으면 null 반환 (조회에서 사용)
     */
    private String resolveUserEmail(boolean required) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("인증되지 않은 요청입니다.");
        }

        // JwtAuthFilter 에서 Authentication.name 을 우리 userId 로 세팅했다고 가정
        String userId = auth.getName();
        String googleEmail = userService.getGoogleEmail(userId);

        if ((googleEmail == null || googleEmail.isBlank()) && required) {
            throw new IllegalStateException("연결된 구글 계정이 없습니다. 먼저 동기화를 진행해 주세요.");
        }

        return googleEmail;
    }

    /** 기존 시그니처 유지: "반드시 구글 이메일 있어야 하는" 경우에 사용 */
    private String resolveUserEmail() {
        return resolveUserEmail(true);
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

    /**
     * JWT 로부터 현재 유저를 찾아 UserService 로 구글 이메일을 가져온 뒤 동기화
     * (여기서는 구글 이메일 반드시 있어야 하므로 required=true)
     */
    public List<CalendarEventDto> fetchAndSaveAllEvents(OAuth2AuthorizedClient client)
            throws GeneralSecurityException, IOException {

        String email = resolveUserEmail(true);
        return fetchAndSaveAllEvents(client, email);
    }

    /**
     * 구글 이메일을 명시적으로 받아서 동기화 (JwtLoginSuccessHandler 에서 사용)
     */
    public List<CalendarEventDto> fetchAndSaveAllEvents(OAuth2AuthorizedClient client,
                                                        String userEmail)
            throws GeneralSecurityException, IOException {

        // ✅ 람다에서 캡처 가능한 final 변수로 고정
        final String email = (userEmail == null || userEmail.isBlank())
                ? resolveUserEmail(true)   // 동기화는 필수로 구글 계정 있어야 함
                : userEmail;

        Calendar calendar = buildCalendarClient(client);
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
        String email = resolveUserEmail(true);   // 생성은 구글 계정 필수
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
        String email = resolveUserEmail(true);   // 수정도 구글 계정 필수
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

    /** 전체 조회: 구글 계정이 아직 안 연결된 유저면 그냥 빈 배열 반환 */
    public List<CalendarEventDto> listAllForUser() {
        String email = resolveUserEmail(false);  // ❗ required=false

        if (email == null || email.isBlank()) {
            // 아직 구글 동기화 안 한 유저 → 그냥 빈 배열
            return Collections.emptyList();
        }

        return repository.findByUserEmailOrderByStartTimestampAsc(email);
    }

    public List<CalendarEventDto> listRangeForUser(Long startTs, Long endTs) {
        String email = resolveUserEmail(false);  // ❗ required=false

        if (email == null || email.isBlank()) {
            return Collections.emptyList();
        }

        if (startTs == null || endTs == null) {
            return repository.findByUserEmailOrderByStartTimestampAsc(email);
        }

        return repository.findByUserEmailAndStartTimestampBetweenOrderByStartTimestampAsc(
                email, startTs, endTs
        );
    }
}

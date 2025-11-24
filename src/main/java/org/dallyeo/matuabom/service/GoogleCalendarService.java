package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.Channel;
import java.time.format.DateTimeParseException;
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

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_OFFSET_DT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Pattern DATE_ONLY_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Value("${app.backend-base-url:http://localhost:8080}")
    private String backendBaseUrl;
    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarService.class);

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

        // ✅ FIX: CustomPrincipal에서 정확한 ID를 추출
        if (auth.getPrincipal() instanceof CustomPrincipal) {
            CustomPrincipal principal = (CustomPrincipal) auth.getPrincipal();
            return principal.getUserId();
        }

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

        // CalendarEventDto에 Google Event ID를 @Id 필드(id)에 설정합니다.
        return CalendarEventDto.builder()
                .id(event.getId())
                .userId(userKey) // 🔥 FIX: userEmail 대신 userId에 설정
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
        // 1. 기본 정보 설정 (Location 제거함)
        Event event = new Event()
            .setSummary(req.getTitle())
            .setDescription(req.getDescription());

        EventDateTime start = new EventDateTime();
        EventDateTime end = new EventDateTime();

        // 2. [핵심] All Day 날짜 파싱 로직 (여기가 버그 수정 포인트!)
        if (Boolean.TRUE.equals(req.getAllDay())) {
            LocalDate sDate;
            try {
                // [1순위] AI가 주는 긴 ISO 포맷 (2025-11-29T...) 파싱 시도
                sDate = OffsetDateTime.parse(req.getStart(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDate();
            } catch (Exception e) {
                // [2순위] 실패하면 기존의 짧은 날짜 포맷 시도
                try {
                    sDate = LocalDate.parse(req.getStart(), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ex) {
                    // [3순위] 진짜 다 안되면 오늘 날짜 (안전장치)
                    sDate = LocalDate.now(zone);
                }
            }

            LocalDate eDate;
            try {
                // 종료일도 똑같이 처리
                eDate = OffsetDateTime.parse(req.getEnd(), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDate();
            } catch (Exception e) {
                try {
                    eDate = LocalDate.parse(req.getEnd(), DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ex) {
                    eDate = sDate.plusDays(1);
                }
            }

            // 날짜 역전 방지
            if (!eDate.isAfter(sDate)) eDate = sDate.plusDays(1);

            // 구글은 종일 일정일 때 setDate 사용 (String "yyyy-MM-dd")
            start.setDate(new DateTime(sDate.toString()));
            end.setDate(new DateTime(eDate.toString()));

        } else {
            // [시간 일정] AI가 준 ISO 문자열을 구글 DateTime 객체가 바로 인식함
            start.setDateTime(new DateTime(req.getStart()));
            start.setTimeZone(zone.getId());

            end.setDateTime(new DateTime(req.getEnd()));
            end.setTimeZone(zone.getId());
        }

        event.setStart(start);
        event.setEnd(end);

        return event;
    }

    /* ============================================================
       📌 전체 동기화 (구글 → Atlas)
       ============================================================ */

    public List<CalendarEventDto> fetchAndSaveAllEvents(
            GoogleOAuthClientEntity tokens,
            String userKey
    ) throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(tokens);
        String resolvedKey = (userKey == null || userKey.isBlank()) ? resolveUserKey() : userKey;
        ZoneId zone = DEFAULT_ZONE;

        // 1) --- (A) syncToken 확보용 호출: singleEvents = false
        String page = null;
        String lastSyncToken = null;
        do {
            Events evPage = calendar.events()
                    .list("primary")
                    .setSingleEvents(false)      // <-- non-expanded ─ for sync token
                    .setShowDeleted(true)       // include deletions so token is useful
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (evPage.getNextSyncToken() != null && !evPage.getNextSyncToken().isBlank()) {
                lastSyncToken = evPage.getNextSyncToken();
            }
            page = evPage.getNextPageToken();
        } while (page != null);

        // syncToken을 tokens 객체에 저장 (DB 저장은 상위 서비스 GoogleSyncService에서 담당)
        if (lastSyncToken != null && !lastSyncToken.isBlank()) {
            logger.info("Setting initial syncToken for user {}: {}", tokens.getUserId(), lastSyncToken);
            tokens.setSyncToken(lastSyncToken);
        } else {
            logger.warn("No nextSyncToken obtained during sync-token scan for user {}", tokens.getUserId());
        }

        // 2) --- (B) UI용 전체 이벤트 수집: singleEvents = true (expanded instances)
        List<Event> allExpanded = new ArrayList<>();
        page = null;
        do {
            Events evPage = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)      // <-- expanded instances for UI
                    .setShowDeleted(false)     // we will handle deletions separately if needed
                    .setOrderBy("startTime")
                    .setTimeMin(new DateTime(0L))
                    .setMaxResults(2500)
                    .setPageToken(page)
                    .execute();

            if (evPage.getItems() != null) allExpanded.addAll(evPage.getItems());
            page = evPage.getNextPageToken();
        } while (page != null);

        // 이전 색상 등 보존
        Map<String,String> previousColors = new HashMap<>();
        // 🔥 FIX: findByUserEmail... -> findByUserId...로 변경
        repository.findByUserIdOrderByStartTimestampAsc(resolvedKey)
                .forEach(e -> { if (e.getColor() != null) previousColors.put(e.getId(), e.getColor()); });

        // 변환 + 저장
        List<CalendarEventDto> dtos = allExpanded.stream()
                .map(e -> {
                    CalendarEventDto dto = toDto(e, resolvedKey, zone);
                    if (previousColors.containsKey(dto.getId())) dto.setColor(previousColors.get(dto.getId()));
                    // DTO의 ID는 Google ID로 설정되어 있어야 합니다 (@Id 필드)
                    dto.setId(e.getId());
                    return dto;
                })
                .collect(Collectors.toList());

        // 🔥 FIX: deleteByUserEmail -> deleteByUserId로 변경
        repository.deleteByUserId(resolvedKey);
        repository.saveAll(dtos);

        return dtos;
    }


    /* ============================================================
       📌 구글 + DB: 생성
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

        // DTO의 'id' 필드는 Google Event ID가 됩니다.
        CalendarEventDto dto = toDto(created, userKey, zone);

        if (req.getColor() != null)
            dto.setColor(req.getColor());

        // 이 DTO는 @Id 필드(id)에 Google ID를 가지고 저장됩니다.
        return repository.save(dto);
    }

    /* ============================================================
       📌 구글 + DB: 수정
       ============================================================ */

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

        // (날짜/시간 처리 로직 ... 생략 없음)
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
        // (날짜/시간 처리 로직 끝)

        // Google에 업데이트
        Event updated = calendar.events()
                .update("primary", eventId, existing)
                .execute();

        // DTO 변환 (ID = Google ID)
        CalendarEventDto dto = toDto(updated, userKey, zone);

        // 색상 처리
        if (req.getColor() != null) {
            dto.setColor(req.getColor());
        } else {
            // DB에서 기존 색상 가져오기 (Upsert이므로)
            repository.findById(eventId)
                    .map(CalendarEventDto::getColor)
                    .ifPresent(dto::setColor);
        }

        // DB에 저장 (ID 기준 덮어쓰기)
        return repository.save(dto);
    }

    /* ============================================================
       📌 구글 + DB: 삭제
       ============================================================ */

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
                 logger.warn("Event {} already deleted in Google (404/410). Ignoring.", eventId);
                // no-op
            }
            // 401/403 (인증/권한) 에러 로깅 강화
            else if (code == 401 || code == 403) {
                logger.error("Google API auth error (401/403) deleting event {}. Check tokens for user {}.", eventId, userKey, e);
            }
            // 그 외 → 다시 던져서 500
            else {
                logger.error("Google API error deleting event {}.", eventId, e);
                throw e;
            }
        }

        // 2) 우리 DB에서도 삭제 (권한 체크)
        repository.findById(eventId).ifPresent(ev -> {
            // 🔥 FIX: getUserEmail -> getUserId로 변경
            if (!Objects.equals(ev.getUserId(), userKey)) {
                logger.warn("User {} tried to delete event {} owned by {}", userKey, eventId, ev.getUserId());
                throw new IllegalStateException("권한이 없는 일정입니다.");
            }
            repository.deleteById(eventId);
        });
    }

    /* ============================================================
       📌 로컬 전용 (DB-only)
       ============================================================ */

    // ✅ FIX: uid 매개변수 추가
    public CalendarEventDto createLocalEvent(String uid, CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;

        boolean allDay =
                Boolean.TRUE.equals(req.getAllDay()) ||
                        looksLikeDateOnly(req.getStart());

        long sTs, eTs;
        String sIso, eIso;

        if (allDay) {
            System.out.println("====== [DEBUG] allDay 로직 시작 ======");
            System.out.println("입력된 Start 값: " + req.getStart());
            System.out.println("입력된 End 값:   " + req.getEnd());

            LocalDate s;
            try {
                // 1. 공백 제거 (혹시 모를 공백 때문일 수 있음)
                String startStr = req.getStart().trim();

                // 2. AI가 주는 ISO 포맷 (2025-11-29T00:00:00+09:00) 파싱 시도
                // ISO_DATE_TIME은 Offset이 있든 없든 웬만하면 다 받아줍니다.
                s = OffsetDateTime.parse(startStr, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
                System.out.println(">>> [성공] ISO 파싱 성공: " + s);

            } catch (Exception e1) {
                System.err.println(">>> [실패] 1차 ISO 파싱 실패: " + e1.getMessage());

                // 3. 실패 시 타임스탬프(숫자)인지 확인
                try {
                    long millis = Long.parseLong(req.getStart().trim());
                    s = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
                    System.out.println(">>> [성공] 타임스탬프 파싱 성공: " + s);
                } catch (Exception e2) {
                    System.err.println(">>> [실패] 2차 타임스탬프 실패: " + e2.getMessage());

                    // 4. 실패 시 짧은 날짜(2025-11-29)인지 확인
                    try {
                        s = LocalDate.parse(req.getStart().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                        System.out.println(">>> [성공] LocalDate 파싱 성공: " + s);
                    } catch (Exception e3) {
                        System.err.println(">>> [최종 실패] 모든 파싱 실패. 오늘 날짜로 설정합니다.");
                        s = LocalDate.now(zone);
                    }
                }
            }

            // 종료일(e) 처리 - 시작일(s)과 같은 로직 적용
            LocalDate e;
            try {
                String endStr = req.getEnd().trim();
                e = OffsetDateTime.parse(endStr, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
            } catch (Exception e1) {
                try {
                    long millis = Long.parseLong(req.getEnd().trim());
                    e = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
                } catch (Exception e2) {
                    try {
                        e = LocalDate.parse(req.getEnd().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (Exception e3) {
                        e = s.plusDays(1); // 실패 시 시작일 다음날
                    }
                }
            }

            // 날짜 역전 방지
            if (!e.isAfter(s)) e = s.plusDays(1);

            ZonedDateTime sz = s.atStartOfDay(zone);
            ZonedDateTime ez = e.atStartOfDay(zone);

            sTs = sz.toInstant().toEpochMilli();
            eTs = ez.toInstant().toEpochMilli();

            sIso = s.toString();
            eIso = e.toString();

            System.out.println("====== [DEBUG] 최종 결정된 날짜: " + sIso + " ~ " + eIso + " ======");

        }else {

            Instant s = parseDate(req.getStart(), zone);
            Instant e = parseDate(req.getEnd(), zone);

            if (s == null) s = Instant.now();
            if (e == null || !e.isAfter(s)) e = s.plus(Duration.ofHours(1));

            sTs = s.toEpochMilli();
            eTs = e.toEpochMilli();

            sIso = s.atZone(zone).toString();
            eIso = e.atZone(zone).toString();
        }

        // 로컬 전용 이벤트는 UUID를 ID로 사용합니다.
        CalendarEventDto dto = CalendarEventDto.builder()
                .id(UUID.randomUUID().toString())
                .userId(uid) // 🔥 FIX: userEmail 대신 userId에 설정
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

    // ✅ FIX: uid 매개변수 추가
    public CalendarEventDto updateLocalEvent(String uid, String eventId, CreateEventReq req) {

        ZoneId zone = DEFAULT_ZONE;

        CalendarEventDto existing = repository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("event not found: " + eventId));

        // 🔥 FIX: getUserEmail -> getUserId로 변경
        if (!existing.getUserId().equals(uid))
            throw new IllegalStateException("권한이 없는 일정입니다.");

        // (날짜/시간 처리 로직 ... 생략 없음)
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
        // (날짜/시간 처리 로직 끝)

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

    // ✅ FIX: uid 매개변수 추가
    public void deleteLocalEvent(String uid, String eventId) {

        repository.findById(eventId)
                .ifPresent(ev -> {
                    // 🔥 FIX: getUserEmail -> getUserId로 변경
                    if (!ev.getUserId().equals(uid))
                        throw new IllegalStateException("권한 없음");

                    repository.deleteById(eventId);
                });
    }

    /* ============================================================
       📌 증분 동기화 (구글 → Atlas)
       ============================================================ */
    public void incrementalSync(GoogleOAuthClientEntity tokens)
            throws GeneralSecurityException, IOException {

        String userKey = tokens.getUserId();
        ZoneId zone = DEFAULT_ZONE;

        if (tokens.getSyncToken() == null || tokens.getSyncToken().isBlank()) {
            logger.warn("Sync token missing for user {}. Performing full sync.", userKey);
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
                logger.warn("Sync token expired (410 Gone) for user {}. Retrying with full sync.", userKey);
                tokens.setSyncToken(null); // syncToken 제거
                fetchAndSaveAllEvents(tokens, userKey); // 전체 동기화 다시 실행
                return;
            }
            throw e;
        }

        if (events.getItems() != null) {
            for (Event ev : events.getItems()) {
                String eventId = ev.getId();

                boolean deleted = "cancelled".equals(ev.getStatus());

                if (deleted) {
                    // Upsert 로직 (수정 시 중복 생성 방지)
                    Optional<CalendarEventDto> eventOpt = repository.findById(eventId);
                    if (eventOpt.isPresent()) {
                        CalendarEventDto dto = eventOpt.get();
                        // 🔥 FIX: getUserEmail -> getUserId로 변경
                        if (Objects.equals(dto.getUserId(), userKey)) {
                            repository.delete(dto); // ID 대신 엔티티로 삭제
                            logger.info("Deleted event {} (Webhook cancelled) for user {}", eventId, userKey);
                        } else {
                            logger.warn("User {} mismatch on delete webhook for event {}", userKey, eventId);
                        }
                    } else {
                        // DB에 없는 이벤트의 삭제 알림 (이미 삭제되었거나, 로컬 생성 후 동기화 전)
                        logger.warn("Event {} not found in DB for webhook delete.", eventId);
                    }
                } else {
                    // Upsert 로직 (수정 시 중복 생성 방지)
                    CalendarEventDto dtoFromGoogle = toDto(ev, userKey, zone);

                    repository.findById(eventId).ifPresentOrElse(existing -> {
                        // (존재하는 경우 - 수정)
                        dtoFromGoogle.setColor(existing.getColor()); // 기존 로컬 색상 유지
                        dtoFromGoogle.setId(existing.getId()); // @Id가 Google ID이므로 명시적 설정
                        repository.save(dtoFromGoogle);
                        logger.info("Updated event {} (Webhook modified) for user {}", eventId, userKey);
                    }, () -> {
                        // (존재하지 않는 경우 - 생성)
                        dtoFromGoogle.setId(eventId); // @Id가 Google ID이므로 명시적 설정
                        repository.save(dtoFromGoogle);
                        logger.info("Created new event {} (Webhook added) for user {}", eventId, userKey);
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
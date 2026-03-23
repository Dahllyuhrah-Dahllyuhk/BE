package org.dallyeo.matuabom.admin.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.calendar.service.WatchChannelRenewalService;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserJpaRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final WatchChannelRenewalService watchChannelRenewalService;

    @Value("${app.admin-secret:admin1234}")
    private String adminSecret;

    /** 관리자 인증: 요청 헤더 X-Admin-Secret 으로 검증 */
    private boolean isAuthorized(String secret) {
        return adminSecret.equals(secret);
    }

    // ── 대시보드 종합 통계 ─────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestHeader(value = "X-Admin-Secret", required = false) String secret) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        long totalUsers    = userRepository.count();
        long totalMeetings = meetingRepository.count();
        long totalEvents   = calendarEventRepository.count();

        long pendingMeetings   = meetingRepository.findAll().stream()
                .filter(m -> "PENDING".equals(m.getStatus())).count();
        long confirmedMeetings = meetingRepository.findAll().stream()
                .filter(m -> "CONFIRMED".equals(m.getStatus())).count();
        long closedMeetings    = meetingRepository.findAll().stream()
                .filter(m -> "CLOSED".equals(m.getStatus())).count();

        // 최근 7일 신규 가입자
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        long newUsersThisWeek = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(weekAgo))
                .count();

        // Google 연동 사용자 수
        long googleLinkedUsers = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.isGoogleLinked()))
                .count();

        // 평균 참여자 수
        OptionalDouble avgParticipants = meetingRepository.findAll().stream()
                .filter(m -> m.getParticipants() != null)
                .mapToInt(m -> m.getParticipants().size())
                .average();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("newUsersThisWeek", newUsersThisWeek);
        result.put("googleLinkedUsers", googleLinkedUsers);
        result.put("googleLinkedRate", totalUsers > 0
                ? Math.round((double) googleLinkedUsers / totalUsers * 100) : 0);
        result.put("totalMeetings", totalMeetings);
        result.put("pendingMeetings", pendingMeetings);
        result.put("confirmedMeetings", confirmedMeetings);
        result.put("closedMeetings", closedMeetings);
        result.put("totalCalendarEvents", totalEvents);
        result.put("avgParticipantsPerMeeting",
                avgParticipants.isPresent() ? Math.round(avgParticipants.getAsDouble() * 10) / 10.0 : 0);

        return ResponseEntity.ok(result);
    }

    // ── 사용자 목록 ───────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> users(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        var all = userRepository.findAll();
        int total = all.size();
        int from  = Math.min(page * size, total);
        int to    = Math.min(from + size, total);

        // 최신 가입순 정렬
        all.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        List<Map<String, Object>> users = all.subList(from, to).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getMongoId());
            m.put("nickname", u.getNickname());
            m.put("googleLinked", u.isGoogleLinked());
            m.put("googleEmail", u.getGoogleEmail());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("users", users);
        return ResponseEntity.ok(result);
    }

    // ── 모임 목록 ─────────────────────────────────────────────────────

    @GetMapping("/meetings")
    public ResponseEntity<?> meetings(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        var all = meetingRepository.findAll();
        int total = all.size();

        // 최신순 정렬
        all.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        int from = Math.min(page * size, total);
        int to   = Math.min(from + size, total);

        List<Map<String, Object>> meetings = all.subList(from, to).stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("name", m.getName());
            item.put("status", m.getStatus());
            item.put("hostUserId", m.getHostUserId());
            item.put("participantCount", m.getParticipants() != null ? m.getParticipants().size() : 0);
            item.put("createdAt", m.getCreatedAt());
            item.put("confirmedStart", m.getConfirmedStart());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("meetings", meetings);
        return ResponseEntity.ok(result);
    }

    // ── 일별 신규 가입자 추이 (최근 30일) ────────────────────────────

    @GetMapping("/stats/signups")
    public ResponseEntity<?> signupTrend(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);

        Map<LocalDate, Long> countByDate = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getCreatedAt().atZone(zone).toLocalDate(),
                        Collectors.counting()
                ));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("count", countByDate.getOrDefault(d, 0L));
            trend.add(row);
        }
        return ResponseEntity.ok(trend);
    }

    // ── 일별 모임 생성 추이 (최근 30일) ──────────────────────────────

    @GetMapping("/stats/meetings")
    public ResponseEntity<?> meetingTrend(            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);

        Map<LocalDate, Long> countByDate = meetingRepository.findAll().stream()
                .filter(m -> m.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getCreatedAt().atZone(zone).toLocalDate(),
                        Collectors.counting()
                ));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("count", countByDate.getOrDefault(d, 0L));
            trend.add(row);
        }
        return ResponseEntity.ok(trend);
    }

    // ── Google Watch 채널 즉시 갱신 ───────────────────────────────────

    @PostMapping("/watch-channels/renew")
    public ResponseEntity<?> renewWatchChannels(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        if (!isAuthorized(secret)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        watchChannelRenewalService.renewExpiringChannels();
        return ResponseEntity.ok(Map.of("message", "Watch channel renewal triggered"));
    }
}

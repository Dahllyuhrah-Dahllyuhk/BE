package org.dallyeo.matuabom.controller;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/calendar", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class CalendarController {

    private final GoogleCalendarService service;

    /**
     * 이벤트 목록
     * - startTs/endTs 둘 다 주면 해당 범위에 "겹치는" 이벤트만 반환
     * - 없으면 전체(해당 사용자) 반환
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> list(
            @RequestParam(required = false) Long startTs,
            @RequestParam(required = false) Long endTs
    ) {
        if (startTs != null && endTs != null) {
            return ResponseEntity.ok(service.listRangeForUser(startTs, endTs));
        }
        return ResponseEntity.ok(service.listAllForUser());
    }

    /**
     * Google ↔ DB 풀 동기화
     */
    @PostMapping("/sync")
    public ResponseEntity<List<CalendarEventDto>> syncAll(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client
    ) throws GeneralSecurityException, IOException {
        return ResponseEntity.ok(service.fetchAndSaveAllEvents(client));
    }

    /**
     * 이벤트 생성
     * - DB 저장 + Google Calendar 반영
     * - allDay=true 인 경우: Google API 규격대로 date/dateTime 필드를 서비스에서 알아서 설정
     */
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @RequestBody CreateEventReq req
    ) throws GeneralSecurityException, IOException {
        try {
            // 최소 검증(제목/시간)
            String msg = basicValidate(req);
            if (msg != null) {
                return badRequest("validation_error", msg);
            }
            CalendarEventDto created = service.createEvent(client, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (GoogleJsonResponseException gjre) {
            return handleGoogleError(gjre, "create");
        }
    }

    /**
     * 이벤트 수정
     * - DB 업데이트 + Google Calendar 반영
     */
    @PutMapping(value = "/events/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @PathVariable String id,
            @RequestBody CreateEventReq req
    ) throws GeneralSecurityException, IOException {
        try {
            String msg = basicValidate(req);
            if (msg != null) {
                return badRequest("validation_error", msg);
            }
            CalendarEventDto updated = service.updateEvent(client, id, req);
            return ResponseEntity.ok(updated);
        } catch (GoogleJsonResponseException gjre) {
            return handleGoogleError(gjre, "update");
        }
    }

    /**
     * 이벤트 삭제
     * - DB 삭제 + Google Calendar 삭제
     * - Google 측에서 이미 삭제(410 Gone)여도 멱등성 관점에서 204로 처리
     */
    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> delete(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @PathVariable String id
    ) throws GeneralSecurityException, IOException {
        try {
            service.deleteEvent(client, id);
            return ResponseEntity.noContent().build();
        } catch (GoogleJsonResponseException gjre) {
            if (gjre.getStatusCode() == 410) { // Resource has been deleted
                // 이미 Google 쪽 리소스가 사라졌다면 DB만 정리되어도 성공으로 간주
                return ResponseEntity.noContent().build();
            }
            return handleGoogleError(gjre, "delete");
        }
    }

    /* ===================== 유틸리티 ===================== */

    /** 프론트 기본 검증 실패 시 400 응답을 JSON으로 반환 */
    private ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 공통 최소 검증
     * - title 필수
     * - allDay=true: start/end는 "yyyy-MM-dd" 형식 권장(서비스에서 보정 가능)
     * - allDay=false: start/end ISO-8601 date-time 권장
     */
    private String basicValidate(CreateEventReq req) {
        if (req == null) return "request body is null";
        if (req.getTitle() == null || req.getTitle().isBlank()) return "title is required";
        if (req.getStart() == null || req.getEnd() == null) return "start/end are required";
        return null;
    }

    /**
     * Google API 예외를 사용자 친화적으로 매핑
     * - 401/403: 인증/스코프 문제(캘린더 쓰기 권한 필요: https://www.googleapis.com/auth/calendar.events)
     * - 400: 잘못된 요청(날짜 형식, date/dateTime 혼용 등)
     * - 그 외: 원본 코드/메시지 전달
     */
    private ResponseEntity<Map<String, Object>> handleGoogleError(GoogleJsonResponseException gjre, String op) {
        int code = gjre.getStatusCode();
        String reason = gjre.getDetails() != null && gjre.getDetails().getErrors() != null
                && !gjre.getDetails().getErrors().isEmpty()
                ? gjre.getDetails().getErrors().get(0).getReason()
                : "unknown";
        String message = gjre.getDetails() != null ? gjre.getDetails().getMessage() : gjre.getMessage();

        Map<String, Object> body = new HashMap<>();
        body.put("operation", op);
        body.put("google_status", code);
        body.put("google_reason", reason);
        body.put("google_message", message);

        HttpStatus status;
        // 대표적 매핑
        if (code == 401) {
            status = HttpStatus.UNAUTHORIZED;
            body.put("hint", "로그인이 필요합니다.");
        } else if (code == 403) {
            status = HttpStatus.FORBIDDEN;
            body.put("hint", "캘린더 쓰기 스코프가 부족합니다. spring oauth2 scope에 https://www.googleapis.com/auth/calendar.events 포함 필요.");
        } else if (code == 400) {
            status = HttpStatus.BAD_REQUEST;
            body.put("hint", "요청 본문을 확인하세요. allDay=true면 date(dateTime 아님), end는 익일 00:00(exclusive).");
        } else if (code == 404) {
            status = HttpStatus.NOT_FOUND;
        } else {
            status = HttpStatus.BAD_GATEWAY; // 외부 API 오류를 502로 표현
        }
        return ResponseEntity.status(status).body(body);
    }
}

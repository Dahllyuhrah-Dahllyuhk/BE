package org.dallyeo.matuabom.controller;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.service.GoogleCalendarService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
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
    private final OAuth2AuthorizedClientService authorizedClientService;

    /* -----------------------------------------------------------
     * 이벤트 목록 조회
     * ----------------------------------------------------------- */
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

    /* -----------------------------------------------------------
     * Google Client 조회 (⭐ 수정된 부분)
     * ----------------------------------------------------------- */
    private OAuth2AuthorizedClient getGoogleClient() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        // ⭐ 핵심: principal = userId 로 JWTAuthFilter가 설정해둠
        Object principal = auth.getPrincipal();
        if (!(principal instanceof String)) {
            return null;
        }

        String userId = (String) principal;

        // ⭐ 이제 userId로 Google client 로드
        return authorizedClientService.loadAuthorizedClient("google", userId);
    }

    /* -----------------------------------------------------------
     * 이벤트 생성
     * ----------------------------------------------------------- */
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody CreateEventReq req) {
        OAuth2AuthorizedClient googleClient = getGoogleClient();

        if (googleClient == null) {
            Map<String, Object> body = Map.of(
                    "error", "google_login_required",
                    "message", "Google 캘린더 연동을 먼저 진행해주세요."
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        String msg = basicValidate(req);
        if (msg != null) {
            return badRequest("validation_error", msg);
        }

        try {
            CalendarEventDto created = service.createGoogleEvent(googleClient, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (GoogleJsonResponseException gjre) {
            return handleGoogleError(gjre, "create");
        } catch (GeneralSecurityException | IOException ex) {
            return serverError("create", ex);
        }
    }

    /* -----------------------------------------------------------
     * 이벤트 수정
     * ----------------------------------------------------------- */
    @PutMapping(value = "/events/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody CreateEventReq req
    ) {
        OAuth2AuthorizedClient googleClient = getGoogleClient();

        if (googleClient == null) {
            Map<String, Object> body = Map.of(
                    "error", "google_login_required",
                    "message", "Google 캘린더 연동을 먼저 진행해주세요."
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        String msg = basicValidate(req);
        if (msg != null) {
            return badRequest("validation_error", msg);
        }

        try {
            CalendarEventDto updated = service.updateGoogleEvent(googleClient, id, req);
            return ResponseEntity.ok(updated);
        } catch (GoogleJsonResponseException gjre) {
            return handleGoogleError(gjre, "update");
        } catch (GeneralSecurityException | IOException ex) {
            return serverError("update", ex);
        }
    }

    /* -----------------------------------------------------------
     * 이벤트 삭제
     * ----------------------------------------------------------- */
    @DeleteMapping("/events/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {

        OAuth2AuthorizedClient googleClient = getGoogleClient();

        if (googleClient == null) {
            Map<String, Object> body = Map.of(
                    "error", "google_login_required",
                    "message", "Google 캘린더 연동을 먼저 진행해주세요."
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        try {
            service.deleteGoogleEvent(googleClient, id);
            return ResponseEntity.noContent().build();
        } catch (GoogleJsonResponseException gjre) {
            return handleGoogleError(gjre, "delete");
        } catch (GeneralSecurityException | IOException ex) {
            return serverError("delete", ex);
        }
    }

    /* -----------------------------------------------------------
     * 유틸리티
     * ----------------------------------------------------------- */

    private ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", code, "message", message));
    }

    private String basicValidate(CreateEventReq req) {
        if (req == null) return "request body is null";
        if (req.getTitle() == null || req.getTitle().isBlank()) return "title is required";
        if (req.getStart() == null || req.getEnd() == null) return "start/end are required";
        return null;
    }

    private ResponseEntity<Map<String, Object>> handleGoogleError(GoogleJsonResponseException gjre, String op) {
        int code = gjre.getStatusCode();
        String reason = (gjre.getDetails() != null && gjre.getDetails().getErrors() != null &&
                         !gjre.getDetails().getErrors().isEmpty())
                        ? gjre.getDetails().getErrors().get(0).getReason()
                        : "unknown";

        String message = gjre.getDetails() != null
                ? gjre.getDetails().getMessage()
                : gjre.getMessage();

        Map<String, Object> body = new HashMap<>();
        body.put("operation", op);
        body.put("google_status", code);
        body.put("google_reason", reason);
        body.put("google_message", message);

        HttpStatus status;
        if (code == 401) status = HttpStatus.UNAUTHORIZED;
        else if (code == 403) status = HttpStatus.FORBIDDEN;
        else if (code == 400) status = HttpStatus.BAD_REQUEST;
        else if (code == 404) status = HttpStatus.NOT_FOUND;
        else status = HttpStatus.BAD_GATEWAY;

        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Map<String, Object>> serverError(String op, Exception e) {
        Map<String, Object> body = Map.of(
                "operation", op,
                "message", e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}

// src/main/java/org/dallyeo/matuabom/controller/CalendarController.java
package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.service.GoogleCalendarQueryService;
import org.dallyeo.matuabom.service.GoogleCalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final GoogleCalendarService googleCalendarService;
    private final GoogleCalendarQueryService googleCalendarQueryService;

    private String extractEmail(@AuthenticationPrincipal OAuth2User principal,
                                OAuth2AuthorizedClient client) {
        if (principal != null) {
            Object email = principal.getAttributes().get("email");
            if (email != null) return email.toString();
        }
        return client != null ? client.getPrincipalName() : "unknown";
    }

    /** ✅ 전체 기간 동기화 */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncAll(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User principal
    ) throws GeneralSecurityException, IOException {

        List<CalendarEventDto> savedList =
                googleCalendarService.fetchAndSaveAllEvents(authorizedClient);

        String email = extractEmail(principal, authorizedClient);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("saved", savedList.size());
        body.put("userEmail", email);
        return ResponseEntity.ok(body);
    }

    /** (옵션) 오늘 이후만 동기화 */
    @PostMapping("/sync/upcoming")
    public ResponseEntity<Map<String, Object>> syncUpcoming(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @AuthenticationPrincipal OAuth2User principal
    ) throws GeneralSecurityException, IOException {

        List<CalendarEventDto> savedList =
                googleCalendarService.fetchUpcomingAndSave(authorizedClient);

        String email = extractEmail(principal, authorizedClient);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("saved", savedList.size());
        body.put("userEmail", email);
        return ResponseEntity.ok(body);
    }

    /** 조회: 쿼리 없으면 전체(해당 사용자 모든 기간) */
    @GetMapping("/events")
    public List<CalendarEventDto> list(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end
    ) {
        String email = extractEmail(principal, client);

        Long s = null, e = null;
        if (start != null && end != null) {
            s = Instant.parse(start).toEpochMilli();
            e = Instant.parse(end).toEpochMilli();
        }
        return googleCalendarQueryService.query(email, s, e);
    }
}

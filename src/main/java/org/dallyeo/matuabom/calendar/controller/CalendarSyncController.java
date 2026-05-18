package org.dallyeo.matuabom.calendar.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.calendar.service.GoogleSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarSyncController {

    private final GoogleSyncService googleSyncService;

    /**
     * FE 폴링 동기화용 — 인증된 사용자의 구글 캘린더 증분 동기화를 트리거합니다.
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> sync(
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        if (principal != null) {
            googleSyncService.runIncrementalSync(principal.getUserId());
        }
        return ResponseEntity.noContent().build();
    }
}

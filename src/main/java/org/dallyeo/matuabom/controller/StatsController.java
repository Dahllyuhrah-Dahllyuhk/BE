package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.DashboardStats;
import org.dallyeo.matuabom.dto.TopPartnerDto;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.dallyeo.matuabom.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> getDashboard(@AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.ok().body(statsService.getDashboardStats(principal.getUserId(), Instant.now()));
    }

    @GetMapping("/top-partners")
    public ResponseEntity<List<TopPartnerDto>> getPartners(@AuthenticationPrincipal CustomPrincipal principal, @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok().body(statsService.getTopPartners(principal.getUserId(), limit));
    }

    @GetMapping("/this-month")
    public ResponseEntity<Map<String, Integer>> getMyThisMonthStats(
            @AuthenticationPrincipal CustomPrincipal principal
    ) {
        String userId = principal.getUserId();
        int participatedCount = statsService.getMyThisMonthMeetingCount(userId);

        return ResponseEntity.ok(
                Map.of("participatedCount", participatedCount)
        );
    }

}

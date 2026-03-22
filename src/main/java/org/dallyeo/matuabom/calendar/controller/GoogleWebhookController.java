package org.dallyeo.matuabom.calendar.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/google")
@RequiredArgsConstructor
public class GoogleWebhookController {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleSyncService googleSyncService;

    @Value("${app.google-webhook-token:}")
    private String expectedToken;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request) {
        String channelId    = request.getHeader("X-Goog-Channel-ID");
        String state        = request.getHeader("X-Goog-Resource-State");
        String channelToken = request.getHeader("X-Goog-Channel-Token");

        // Webhook token 검증 (설정된 경우에만)
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (!expectedToken.equals(channelToken)) {
                log.warn("Webhook token mismatch. channelId={}", channelId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        // "sync" 는 채널 등록 확인 알림 — 실제 데이터 변경 아님
        if ("sync".equals(state)) {
            return ResponseEntity.noContent().build();
        }

        googleTokens.findByChannelId(channelId).ifPresent(tokens ->
                googleSyncService.runIncrementalSync(tokens.getUserId())
        );

        return ResponseEntity.noContent().build();
    }
}

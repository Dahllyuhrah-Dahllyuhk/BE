package org.dallyeo.matuabom.calendar.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google")
@RequiredArgsConstructor
public class GoogleWebhookController {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleSyncService googleSyncService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(HttpServletRequest request) {
        String channelId  = request.getHeader("X-Goog-Channel-ID");
        String state      = request.getHeader("X-Goog-Resource-State");

        // 최초 채널 등록 확인 알림 — 동기화 불필요
        if ("sync".equals(state)) {
            return ResponseEntity.noContent().build();
        }

        googleTokens.findByChannelId(channelId).ifPresent(tokens -> {
            String userId = tokens.getUserId();
            googleSyncService.runIncrementalSync(userId);
        });

        return ResponseEntity.noContent().build();
    }
}

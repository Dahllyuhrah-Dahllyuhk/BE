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
        String channelId = request.getHeader("X-Goog-Channel-ID");
        String state     = request.getHeader("X-Goog-Resource-State");

        // "sync" 는 채널 등록 확인 알림 — 실제 데이터 변경 아님
        if ("sync".equals(state)) {
            return ResponseEntity.noContent().build();
        }

        // exists / update / delete 등 모든 변경 상태에 대해 증분 동기화 실행
        googleTokens.findByChannelId(channelId).ifPresent(tokens ->
                googleSyncService.runIncrementalSync(tokens.getUserId())
        );

        return ResponseEntity.noContent().build();
    }
}

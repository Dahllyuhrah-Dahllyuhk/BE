package org.dallyeo.matuabom.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSyncService {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final EventSseService eventSseService;

    @Async("googleSyncExecutor")
    public void runIncrementalSync(String userId) {
        try {
            googleTokens.getTokens(userId).ifPresent(tokens -> {
                try {
                    googleCalendarService.incrementalSync(tokens);
                    googleTokens.save(tokens);
                } catch (Exception e) {
                    log.error("incremental sync failed: {}", e.getMessage());
                }
            });
        } catch (Exception ignore) {}

        try {
            eventSseService.sendEventsUpdated();
        } catch (Exception e) {
            log.warn("SSE push skipped: {}", e.getMessage());
        }
    }
}
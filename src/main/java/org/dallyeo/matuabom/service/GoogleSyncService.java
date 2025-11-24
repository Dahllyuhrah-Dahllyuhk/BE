package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSyncService {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final EventSseService eventSseService;

    @Async("googleSyncExecutor")
    public void runInitialSync(String userId) {
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.fetchAndSaveAllEvents(tokens, userId);
                googleTokens.save(tokens);

                try {
                    googleCalendarService.ensureWatchChannel(tokens);
                    googleTokens.save(tokens);
                } catch (Exception ignore) {}

                eventSseService.sendEventsUpdated();

            } catch (GeneralSecurityException | IOException e) {
                log.error("runInitialSync failed: {}", e.getMessage(), e);
            }
        });
    }

    @Async("googleSyncExecutor")
    public void runIncrementalSync(String userId) {
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.incrementalSync(tokens);
                googleTokens.save(tokens);
                eventSseService.sendEventsUpdated();
            } catch (GeneralSecurityException | IOException e) {
                log.error("runIncrementalSync failed: {}", e.getMessage(), e);
            }
        });
    }

    @Async("googleSyncExecutor")
    public void syncCreateAsync(String userId, CreateEventReq req) {
        googleTokens.getTokens(userId)
                .ifPresent(tokens -> {
                    try { googleCalendarService.createGoogleEvent(tokens, userId, req); }
                    catch (Exception e) { log.error("Create failed: {}", e.getMessage(), e); }
                });
    }

    @Async("googleSyncExecutor")
    public void syncUpdateAsync(String userId, String eventId, CreateEventReq req) {
        googleTokens.getTokens(userId)
                .ifPresent(tokens -> {
                    try { googleCalendarService.updateGoogleEvent(tokens, userId, eventId, req); }
                    catch (Exception e) { log.error("Update failed: {}", e.getMessage(), e); }
                });
    }

    @Async("googleSyncExecutor")
    public void syncDeleteAsync(String userId, String eventId) {
        googleTokens.getTokens(userId)
                .ifPresent(tokens -> {
                    try { googleCalendarService.deleteGoogleEvent(tokens, userId, eventId); }
                    catch (Exception e) { log.error("Delete failed: {}", e.getMessage(), e); }
                });
    }
}

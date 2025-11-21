package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 🔥 FIX 4: Slf4j (로깅) import
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
@Slf4j // 🔥 FIX 4: 클래스 레벨에 Slf4j 추가 (logger 자동 주입)
public class GoogleSyncService {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final EventSseService eventSseService;

    /* ===========================
     * 1) 초기 / 증분 동기화
     * =========================== */

    @Async("googleSyncExecutor")
    public void runInitialSync(String userId) {
        log.info("Starting runInitialSync for user {}", userId);
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                // 1. 증분/전체 동기화 실행 (syncToken 확보)
                googleCalendarService.incrementalSync(tokens);
                googleTokens.save(tokens); // syncToken 반영

                // 2. 웹훅 채널 등록 (watch)
                googleCalendarService.ensureWatchChannel(tokens);
                googleTokens.save(tokens); // channelId/resourceId/expiration 반영

                // 3. FE에 알림
                eventSseService.sendEventsUpdated();
                log.info("Finished runInitialSync successfully for user {}", userId);
            } catch (GeneralSecurityException | IOException e) {
                // 🔥 FIX 4: e.printStackTrace() 대신 log.error() 사용
                log.error("runInitialSync failed for user {}: {}", userId, e.getMessage(), e);
            }
        });
    }

    @Async("googleSyncExecutor")
    public void runIncrementalSync(String userId) {
        log.info("Starting runIncrementalSync for user {}", userId);
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.incrementalSync(tokens);
                googleTokens.save(tokens); // 갱신된 syncToken 저장

                eventSseService.sendEventsUpdated();
                log.info("Finished runIncrementalSync successfully for user {}", userId);
            } catch (GeneralSecurityException | IOException e) {
                // 🔥 FIX 4: e.printStackTrace() 대신 log.error() 사용
                log.error("runIncrementalSync failed for user {}: {}", userId, e.getMessage(), e);
            }
        });
    }

    /* ===========================
     * 2) 일정 생성/수정/삭제 비동기 동기화
     * =========================== */

    @Async("googleSyncExecutor")
    public void syncCreateAsync(String userId, CreateEventReq req) {
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.createGoogleEvent(tokens, userId, req);
            } catch (GeneralSecurityException | IOException e) {
                // 🔥 FIX 4: e.printStackTrace() 대신 log.error() 사용
                log.error("syncCreateAsync failed for user {}: {}", userId, e.getMessage(), e);
            }
        });
    }

    @Async("googleSyncExecutor")
    public void syncUpdateAsync(String userId, String eventId, CreateEventReq req) {
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.updateGoogleEvent(tokens, userId, eventId, req);
            } catch (GeneralSecurityException | IOException e) {
                // 🔥 FIX 4: e.printStackTrace() 대신 log.error() 사용
                log.error("syncUpdateAsync failed for user {} event {}: {}", userId, eventId, e.getMessage(), e);
            }
        });
    }

    @Async("googleSyncExecutor")
    public void syncDeleteAsync(String userId, String eventId) {
        googleTokens.getTokens(userId).ifPresent(tokens -> {
            try {
                googleCalendarService.deleteGoogleEvent(tokens, userId, eventId);
                log.info("Successfully synced delete for event {} on Google for user {}", eventId, userId);
            } catch (GeneralSecurityException | IOException e) {
                // 🔥 FIX 4: e.printStackTrace() 대신 log.error() 사용
                log.error("syncDeleteAsync failed for user {} event {}: {}", userId, eventId, e.getMessage(), e);
            }
        });
    }
}
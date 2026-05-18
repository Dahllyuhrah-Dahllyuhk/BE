package org.dallyeo.matuabom.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSyncService {

    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final EventSseService eventSseService;

    @Async("googleSyncExecutor")
    public void runIncrementalSync(String userId) {
        List<CalendarEventDto> changed = new ArrayList<>();
        List<String> deletedIds = new ArrayList<>();
        boolean needsFullRefresh = false;

        try {
            var tokensOpt = googleTokens.getTokens(userId);
            if (tokensOpt.isPresent()) {
                var tokens = tokensOpt.get();
                try {
                    GoogleCalendarService.SyncResult result =
                            googleCalendarService.incrementalSyncWithResult(tokens);
                    changed.addAll(result.getChanged());
                    deletedIds.addAll(result.getDeletedIds());
                    needsFullRefresh = result.isNeedsFullRefresh();
                    googleTokens.save(tokens);
                } catch (Exception e) {
                    log.error("incremental sync failed for userId={}: {}", userId, e.getMessage());
                }
            }
        } catch (Exception ignore) {}

        try {
            if (needsFullRefresh) {
                // syncToken 만료 → FE 전체 새로고침
                eventSseService.sendEventsUpdated(userId);
            } else if (!changed.isEmpty() || !deletedIds.isEmpty()) {
                // 변경된 이벤트만 SSE 전송 → FE에서 해당 이벤트만 state 패치
                eventSseService.sendEventsChanged(userId, changed, deletedIds);
            } else {
                // 변경사항이 감지되지 않았어도 웹훅이 왔다는 것은 변경이 있다는 의미
                // syncToken 타이밍 이슈 등을 대비해 full refresh 트리거
                eventSseService.sendEventsUpdated(userId);
            }
        } catch (Exception e) {
            log.warn("SSE push skipped for userId={}: {}", userId, e.getMessage());
        }
    }
}

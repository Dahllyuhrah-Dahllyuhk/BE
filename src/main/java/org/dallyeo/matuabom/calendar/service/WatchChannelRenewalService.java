package org.dallyeo.matuabom.calendar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.calendar.repository.GoogleOAuthClientRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Google Calendar Watch 채널은 최대 7일 유효.
 * 매일 새벽 3시에 만료 24시간 전인 채널을 자동 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchChannelRenewalService {

    private final GoogleOAuthClientRepository repo;
    private final GoogleCalendarService googleCalendarService;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    public void renewExpiringChannels() {
        Instant threshold = Instant.now().plusSeconds(86400); // 24시간 이내 만료
        List<GoogleOAuthClientEntity> candidates = repo.findAll().stream()
                .filter(e -> e.getRefreshToken() != null) // Google 연동 사용자만
                .filter(e -> {
                    // watchExpiresAt이 null: 채널이 한 번도 등록된 적 없음
                    //   → watchChannelId도 null인 경우만 등록 대상으로 포함
                    //     (watchChannelId가 있으면 이전에 등록했다가 만료된 케이스)
                    if (e.getWatchExpiresAt() == null) {
                        return e.getWatchChannelId() == null; // 최초 등록 대상만
                    }
                    // 24시간 이내 만료 예정 → 갱신 대상
                    return e.getWatchExpiresAt().isBefore(threshold);
                })
                .toList();

        log.info("WatchChannel renewal: {} channels to process", candidates.size());
        for (GoogleOAuthClientEntity tokens : candidates) {
            try {
                googleCalendarService.ensureWatchChannel(tokens);
                log.info("Watch channel processed for userId={}", tokens.getUserId());
            } catch (Exception e) {
                log.warn("Watch channel renewal failed for userId={}: {}", tokens.getUserId(), e.getMessage());
            }
        }
    }
}

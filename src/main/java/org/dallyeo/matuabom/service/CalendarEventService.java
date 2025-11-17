package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository repository;
    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleCalendarQueryService googleCalendarQueryService;
    private final GoogleSyncService googleSyncService;

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }
        return auth.getName();
    }

    // ==================================================
    // 조회
    // ==================================================
    public List<CalendarEventDto> getEvents(String start, String end)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 구글 연동된 유저라면, 조회 시점에 비동기로 증분 동기화 한 번 태움
        if (googleTokens.isLinked(uid)) {
            googleSyncService.runIncrementalSync(uid);
        }

        // 지금 FE 는 start/end 없이 전체 조회를 사용 중이라
        // 일단은 전체 조회로 두고, 나중에 기간 필터가 필요하면 start/end → epoch 변환해서 넘기면 됨
        return googleCalendarQueryService.query(uid, null, null);
    }

    // ==================================================
    // 생성
    // ==================================================
    public CalendarEventDto create(CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 1) 로컬 DB에 먼저 저장
        CalendarEventDto saved = googleCalendarService.createLocalEvent(req);

        // 2) 구글 연동된 유저면, 구글 쪽은 비동기로 반영
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncCreateAsync(uid, req);
        }

        return saved;
    }

    // ==================================================
    // 수정
    // ==================================================
    public CalendarEventDto update(String eventId, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 소유자 검증 (있으면)
        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        // 1) 로컬 DB 업데이트
        CalendarEventDto updated = googleCalendarService.updateLocalEvent(eventId, req);

        // 2) 구글 연동된 유저면 비동기로 구글 쪽도 업데이트
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncUpdateAsync(uid, eventId, req);
        }

        return updated;
    }

    // ==================================================
    // 삭제
    // ==================================================
    public void delete(String eventId) throws GeneralSecurityException, IOException {
        String uid = userId();

        // 소유자 검증
        repository.findByIdAndUserEmail(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        // 1) 로컬 DB에서 삭제
        googleCalendarService.deleteLocalEvent(eventId);

        // 2) 구글 연동된 유저면 비동기로 구글 일정도 삭제
        if (googleTokens.isLinked(uid)) {
            googleSyncService.syncDeleteAsync(uid, eventId);
        }
    }
}

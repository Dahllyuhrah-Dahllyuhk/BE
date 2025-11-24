package org.dallyeo.matuabom.service;

import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository repository;
    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleCalendarQueryService googleCalendarQueryService;
    private final EventSseService eventSseService;

    // CustomPrincipal로 형변환하여 정확한 userId 추출
    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof CustomPrincipal) {
            CustomPrincipal principal = (CustomPrincipal) auth.getPrincipal();
            return principal.getUserId(); // 정확한 String ID 반환 (예: "6918...")
        }

        // 혹시 모를 호환성 (Principal이 String인 경우)
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }

        throw new IllegalStateException("no authenticated user");
    }

    // ==================================================
    // 조회
    // ==================================================
    public List<CalendarEventDto> getEvents(String start, String end) {
        String uid = userId();
        Long startTs = parseLongOrNull(start);
        Long endTs = parseLongOrNull(end);
        return googleCalendarQueryService.query(uid, startTs, endTs);
    }

    /**
         * 특정 사용자 ID의 캘린더 이벤트를 조회합니다. (모임 추천 로직에서 사용)
         */
    public List<CalendarEventDto> getEventsByUserId(String uid, Long startTs, Long endTs) {
        // 이미 해당 사용자의 데이터가 DB에 동기화되어 있다고 가정하고 쿼리 서비스를 호출합니다.
        return googleCalendarQueryService.query(uid, startTs, endTs);
    }

    //특정 기간의 특정 유저 캘린더 이벤트를 조회합니다
    // 특정 기간 내 특정 유저의 캘린더 이벤트를 키워드로 조회합니다
    public List<CalendarEventDto> getEventsByKeyword(Long startTs, Long endTs, String keyword) {
        String uid = userId();
        Long queryStart = (startTs != null) ? startTs : 0L;
        Long queryEnd = (endTs != null) ? endTs : Long.MAX_VALUE;

        // 2. 분기 처리
        if (keyword == null || keyword.isBlank() || keyword.equals("None")) {
            System.out.println("호이");
            return getEventsByUserId(uid, startTs, endTs );
        } else {

            return repository.findByUserIdAndTitleRegex(uid, keyword, queryEnd, queryStart);
        }
    }



    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================================================
    // 생성
    // ==================================================
    public CalendarEventDto create(CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId(); // 정확한 사용자 ID 확보
        CalendarEventDto saved;

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            saved = googleCalendarService.createGoogleEvent(tokens, uid, req);

        } else {
            // [B. 연동 안 된 사용자]
            // ✅ FIX: createLocalEvent에 uid를 전달
            saved = googleCalendarService.createLocalEvent(uid, req);
        }

        eventSseService.sendEventsUpdated();
        return saved;
    }

    // ==================================================
    // 수정
    // ==================================================
    public CalendarEventDto update(String eventId, CreateEventReq req)
            throws GeneralSecurityException, IOException {

        String uid = userId();

        // 🔥 FIX: findByIdAndUserEmail -> findByIdAndUserId로 변경
        repository.findByIdAndUserId(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        CalendarEventDto updated;

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            updated = googleCalendarService.updateGoogleEvent(tokens, uid, eventId, req);

        } else {
            // [B. 연동 안 된 사용자]
            // ✅ FIX: updateLocalEvent에 uid를 전달
            updated = googleCalendarService.updateLocalEvent(uid, eventId, req);
        }

        eventSseService.sendEventsUpdated();
        return updated;
    }

    // ==================================================
    // 삭제
    // ==================================================
    public void delete(String eventId) throws GeneralSecurityException, IOException {

        String uid = userId();

        // 🔥 FIX: findByIdAndUserEmail -> findByIdAndUserId로 변경
        repository.findByIdAndUserId(eventId, uid)
                .orElseThrow(() -> new IllegalArgumentException("event not found or not owner"));

        if (googleTokens.isLinked(uid)) {
            // [A. 연동된 사용자]
            GoogleOAuthClientEntity tokens = googleTokens
                    .getTokens(uid)
                    .orElseThrow(() -> new IllegalStateException("Google token not found or invalid for user: " + uid));

            googleCalendarService.deleteGoogleEvent(tokens, uid, eventId);

        } else {
            // [B. 연동 안 된 사용자]
            // ✅ FIX: deleteLocalEvent에 uid를 전달
            googleCalendarService.deleteLocalEvent(uid, eventId);
        }

        eventSseService.sendEventsUpdated();
    }
}
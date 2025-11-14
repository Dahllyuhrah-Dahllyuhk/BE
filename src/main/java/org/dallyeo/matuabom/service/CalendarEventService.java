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

    private final UserService userService;
    private final GoogleOAuthClientService googleTokens;
    private final GoogleCalendarService googleCalendarService;
    private final CalendarEventRepository repository;

    /** userKey 가져오기 */
    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }


    // ==================================================
    // 조회
    // ==================================================
    public List<CalendarEventDto> listAll() {
        return repository.findByUserEmailOrderByStartTimestampAsc(userId());
    }


    // ==================================================
    // 생성
    // ==================================================
    public CalendarEventDto create(CreateEventReq req) throws GeneralSecurityException, IOException {

        String uid = userId();

        if (googleTokens.isLinked(uid)) {
            // 구글 연동 → 구글 + DB 저장
            GoogleOAuthClientEntity tokens = googleTokens.getTokens(uid)
                    .orElseThrow();
            return googleCalendarService.createGoogleEvent(tokens, req);
        }

        // DB 전용
        return googleCalendarService.createLocalEvent(req);
    }

    // ==================================================
    // 수정
    // ==================================================
    public CalendarEventDto update(String eventId, CreateEventReq req) throws GeneralSecurityException, IOException {

        String uid = userId();

        if (googleTokens.isLinked(uid)) {
            GoogleOAuthClientEntity tokens = googleTokens.getTokens(uid)
                    .orElseThrow();
            return googleCalendarService.updateGoogleEvent(tokens, eventId, req);
        }

        return googleCalendarService.updateLocalEvent(eventId, req);
    }

    // ==================================================
    // 삭제
    // ==================================================
    public void delete(String eventId) throws GeneralSecurityException, IOException {
        String uid = userId();

        if (googleTokens.isLinked(uid)) {
            GoogleOAuthClientEntity tokens = googleTokens.getTokens(uid)
                    .orElseThrow();
            googleCalendarService.deleteGoogleEvent(tokens, eventId);
        } else {
            googleCalendarService.deleteLocalEvent(eventId);
        }
    }
}


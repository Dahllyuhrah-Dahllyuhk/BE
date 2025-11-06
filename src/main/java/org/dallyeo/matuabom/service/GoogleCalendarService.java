package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final CalendarEventRepository repository;

    /** 구글 캘린더 클라이언트 생성 */
    private Calendar buildCalendarClient(OAuth2AuthorizedClient authorizedClient)
            throws GeneralSecurityException, IOException {

        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        return new Calendar.Builder(
                httpTransport,
                jsonFactory,
                request -> request.getHeaders()
                        .setAuthorization("Bearer " + authorizedClient.getAccessToken().getTokenValue())
        )
        .setApplicationName("Matuabom Calendar Integration")
        .build();
    }

    /** OAuth2 유저 이메일 안전하게 추출 */
    private String resolveUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DefaultOAuth2User oAuth2User) {
            Object email = oAuth2User.getAttributes().get("email");
            if (email != null) return email.toString();
        }
        // fallback (일부 환경에서는 principalName이 이메일일 수도, 아닐 수도 있음)
        return auth != null ? auth.getName() : "unknown";
    }

    /** Google Event -> DTO 변환 */
    private CalendarEventDto toDto(Event event, String userEmail) {
        final String tz = "Asia/Seoul";
        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        Long startTs, endTs;
        String startIso, endIso;

        if (allDay) {
            startTs = event.getStart().getDate().getValue(); // 자정 기준 (날짜)
            endTs   = event.getEnd().getDate().getValue();
            startIso = Instant.ofEpochMilli(startTs).atZone(ZoneId.of(tz)).toLocalDate().toString(); // yyyy-MM-dd
            endIso   = Instant.ofEpochMilli(endTs).atZone(ZoneId.of(tz)).toLocalDate().toString();
        } else {
            startTs = event.getStart().getDateTime().getValue();
            endTs   = event.getEnd().getDateTime().getValue();
            startIso = Instant.ofEpochMilli(startTs).atZone(ZoneId.of(tz)).toString(); // ISO-8601
            endIso   = Instant.ofEpochMilli(endTs).atZone(ZoneId.of(tz)).toString();
        }

        return CalendarEventDto.builder()
                .id(event.getId())
                .userEmail(userEmail)
                .title(event.getSummary())
                .start(startIso)
                .end(endIso)
                .allDay(allDay)
                .startTimestamp(startTs)
                .endTimestamp(endTs)
                .timeZone(tz)
                .build();
    }

    /** 모든 이벤트 가져와서 MongoDB에 저장 후 반환 (full sync) */
    public List<CalendarEventDto> fetchAndSaveAllEvents(OAuth2AuthorizedClient authorizedClient)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(authorizedClient);

        String email = resolveUserEmail();

        List<Event> all = new ArrayList<>();
        String pageToken = null;
        do {
            Events events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)  // 반복 이벤트 전개
                    .setOrderBy("startTime")
                    .setMaxResults(2500)    // 최대치
                    .setPageToken(pageToken)
                    .execute();
            if (events.getItems() != null) {
                all.addAll(events.getItems());
            }
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        List<CalendarEventDto> dtos = all.stream()
                .map(e -> toDto(e, email))
                .toList();

        // 사용자별로 기존 데이터 정리 후 저장 (전체 싹다 삭제가 아니라 사용자 기준 삭제)
        repository.deleteByUserEmail(email);
        repository.saveAll(dtos);

        return dtos;
    }

    /** 오늘 이후만 가져오고 싶을 때 (옵션) */
    public List<CalendarEventDto> fetchUpcomingAndSave(OAuth2AuthorizedClient authorizedClient)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(authorizedClient);
        String email = resolveUserEmail();

        List<Event> all = new ArrayList<>();
        String pageToken = null;
        do {
            Events events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setTimeMin(new com.google.api.client.util.DateTime(System.currentTimeMillis()))
                    .setMaxResults(2500)
                    .setPageToken(pageToken)
                    .execute();
            if (events.getItems() != null) all.addAll(events.getItems());
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        List<CalendarEventDto> dtos = all.stream().map(e -> toDto(e, email)).toList();
        repository.deleteByUserEmail(email);
        repository.saveAll(dtos);
        return dtos;
    }
}

// src/main/java/org/dallyeo/matuabom/service/GoogleCalendarService.java
package org.dallyeo.matuabom.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
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

    /** 저장/조회 모두 동일 규칙으로 이메일을 뽑도록 */
    private String resolveUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof DefaultOAuth2User oAuth2User) {
            Object email = oAuth2User.getAttributes().get("email");
            if (email != null) return email.toString();
        }
        return auth != null ? auth.getName() : "unknown";
    }

    private CalendarEventDto toDto(Event event, String userEmail) {
        final String tz = "Asia/Seoul";
        boolean allDay = event.getStart() != null && event.getStart().getDate() != null;

        Long startTs, endTs;
        String startIso, endIso;

        if (allDay) {
            startTs = event.getStart().getDate().getValue();
            endTs   = event.getEnd().getDate().getValue();
            startIso = Instant.ofEpochMilli(startTs).atZone(ZoneId.of(tz)).toLocalDate().toString();
            endIso   = Instant.ofEpochMilli(endTs).atZone(ZoneId.of(tz)).toLocalDate().toString();
        } else {
            startTs = event.getStart().getDateTime().getValue();
            endTs   = event.getEnd().getDateTime().getValue();
            startIso = Instant.ofEpochMilli(startTs).atZone(ZoneId.of(tz)).toString();
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

    /** ✅ 모든 기간(과거~미래) 전체 동기화 */
    public List<CalendarEventDto> fetchAndSaveAllEvents(OAuth2AuthorizedClient authorizedClient)
            throws GeneralSecurityException, IOException {

        Calendar calendar = buildCalendarClient(authorizedClient);
        String email = resolveUserEmail();

        // 1970-01-01 00:00:00Z 부터 전부
        DateTime min = new DateTime(0L);

        List<Event> all = new ArrayList<>();
        String pageToken = null;
        do {
            Events events = calendar.events()
                    .list("primary")
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setTimeMin(min)           // ✅ 과거부터
                    // .setTimeMax(...)        // ❌ 지정하지 않아 미래 제한 없음
                    .setShowDeleted(false)
                    .setMaxResults(2500)
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

        repository.deleteByUserEmail(email);
        repository.saveAll(dtos);

        return dtos;
    }

    /** (옵션) 오늘 이후만 동기화 */
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
                    .setTimeMin(new DateTime(System.currentTimeMillis())) // 오늘 이후
                    .setShowDeleted(false)
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

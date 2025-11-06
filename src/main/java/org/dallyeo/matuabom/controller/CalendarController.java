package org.dallyeo.matuabom.controller;

import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final GoogleCalendarService googleCalendarService;

//    @GetMapping("/events")
//    public List<Event> getCalendarEvents(
//            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
//    ) throws GeneralSecurityException, IOException {
//        return googleCalendarService.getEvents(authorizedClient);
//    }
//
//    @GetMapping("/events")
//    public List<CalendarEventDto> fetchCalendarEvents(
//            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
//    ) throws IOException {
//        return googleCalendarService.getAndSaveEvents(authorizedClient);
//    }

    /** 전체 동기화 후 반환 */
        @GetMapping("/events")
        public List<CalendarEventDto> syncAll(
                @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient
        ) throws GeneralSecurityException, IOException {
            return googleCalendarService.fetchAndSaveAllEvents(authorizedClient);
        }
}

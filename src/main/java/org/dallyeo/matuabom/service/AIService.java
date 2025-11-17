package org.dallyeo.matuabom.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.dto.Request.AIRequestDTO;
import org.dallyeo.matuabom.dto.Response.AI.AIScheduleResponseDTO;
import org.dallyeo.matuabom.dto.Response.InputCategory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class AIService {
    List<String> options = List.of("bg-blue-500", "bg-purple-500", "bg-green-500", "bg-orange-500", "bg-pink-500");
    Random random = new Random();

    private final WebClient webClient;
    private final CalendarEventService calendarEventService;

    public ResponseEntity<?> call(AIRequestDTO requestDTO)
        throws GeneralSecurityException, IOException {
        AIScheduleResponseDTO response = webClient.post()
            .uri("http://localhost:5000/main")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestDTO)
            .retrieve()
            .bodyToMono(AIScheduleResponseDTO.class)
            .block();

        return ResponseEntity.ok(classify(response));
    }

    public CalendarEventDto classify(AIScheduleResponseDTO response)
        throws GeneralSecurityException, IOException {
        InputCategory inputCategory = response.getCategory();

        if (Objects.requireNonNull(inputCategory) == InputCategory.일정생성) {
            return generateSchedule(response);
        }
        return null;
    }

    public CalendarEventDto generateSchedule(AIScheduleResponseDTO response)
        throws GeneralSecurityException, IOException {
        int index = random.nextInt(options.size());
        CreateEventReq createEventReq = new CreateEventReq();
        createEventReq.setTitle(response.getData().getTitle());
        createEventReq.setDescription(response.getData().getDescription());
        createEventReq.setStart(String.valueOf(response.getData().getStart()));
        createEventReq.setEnd(String.valueOf(response.getData().getEnd()));
        createEventReq.setAllDay(response.getData().getAllDay());
        createEventReq.setColor(options.get(index));

        return  calendarEventService.create(createEventReq);
    }
}

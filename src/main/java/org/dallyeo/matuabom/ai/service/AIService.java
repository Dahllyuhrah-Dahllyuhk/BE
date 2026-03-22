package org.dallyeo.matuabom.ai.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.dto.CreateEventReq;
import org.dallyeo.matuabom.ai.dto.AIRequestDTO;
import org.dallyeo.matuabom.ai.dto.AIServerResponse;
import org.dallyeo.matuabom.ai.dto.AIResponseDTO;
import org.dallyeo.matuabom.ai.dto.InputCategory;
import org.dallyeo.matuabom.meeting.dto.MeetingCreateRequest;
import org.dallyeo.matuabom.meeting.dto.MeetingRequirementDto;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.meeting.service.MeetingService;
import org.dallyeo.matuabom.calendar.service.CalendarEventService;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final MeetingService meetingService;
    private final MeetingRepository meetingRepository;
    private final WebClient webClient;
    private final CalendarEventService calendarEventService;

    List<String> options = List.of("bg-blue-500", "bg-purple-500", "bg-green-500", "bg-orange-500", "bg-pink-500");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public ResponseEntity<AIServerResponse> call(AIRequestDTO requestDTO)
        throws GeneralSecurityException, IOException {

        AIResponseDTO response = webClient.post()
            .uri("http://AI:5000/main")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestDTO)
            .retrieve()
            .bodyToMono(AIResponseDTO.class)
            .block();

        return ResponseEntity.ok(classify(response));
    }

    public AIServerResponse classify(AIResponseDTO response)
        throws GeneralSecurityException, IOException {

        InputCategory category = response.getCategory();
        Object resultData = null;

        if (category != null) {
            switch (category) {
                case SCHEDULE_CREATE:
                    resultData = generateSchedule(response);
                    break;
                case SCHEDULE_READ:
                    resultData = getSchedule(response);
                    break;
                case SCHEDULE_DELETE:
                    resultData = deleteSchedule(response);
                    break;
                case MEETING_CREATE:
                    resultData = generateMeeting(response);
                    break;
                case MEETING_READ:
                    resultData = getMeeting(response);
                    break;
                default:
                    break;
            }
        }

        String summary = "요청을 처리했습니다.";
        if (resultData != null && category != null) {
            try {
                summary = fetchSummaryFromAI(category, resultData);
            } catch (Exception e) {
                log.warn("요약 생성 실패: {}", e.getMessage());
            }
        }

        return AIServerResponse.builder()
            .category(category)
            .summary(summary)
            .data(resultData)
            .build();
    }

    private String fetchSummaryFromAI(InputCategory category, Object resultData) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("category", category.name());
        requestBody.put("data", resultData);

        Map response = webClient.post()
            .uri("http://AI:5000/summary")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        return (String) response.get("summary");
    }

    private List<Meeting> getMeeting(AIResponseDTO response) {
        if (response.getData() instanceof AIResponseDTO.GetMeeting query) {
            String title = query.getTitle();
            String start = query.getDateRangeStart();
            String end = query.getDateRangeEnd();
            String uid = userId();
            String keyword = (title != null) ? title : "";

            if (start != null && end != null) {
                return meetingRepository.searchByTitleAndDateRange(uid, keyword, start, end);
            } else {
                return meetingRepository.searchByTitle(uid, keyword);
            }
        }
        return new ArrayList<>();
    }

    private List<Meeting> generateMeeting(AIResponseDTO response) {
        if (response.getData() instanceof AIResponseDTO.GenerateMeeting data) {
            MeetingCreateRequest request = new MeetingCreateRequest();
            MeetingRequirementDto requirementDto = new MeetingRequirementDto();

            requirementDto.setDateRangeStart(data.getDateRangeStart());
            requirementDto.setDateRangeEnd(data.getDateRangeEnd());
            requirementDto.setIsAllDay(data.getIsAllDay());
            requirementDto.setTimeConstraints(
                data.getTimeConstraints() != null ? data.getTimeConstraints() : new ArrayList<>()
            );

            request.setName(data.getTitle());
            request.setInvitedUserIds(new ArrayList<>());
            request.setRequirement(requirementDto);
            request.setDefaultReflectCalendar(true);
            request.setDefaultReflectTimetable(true);

            String uid = userId();
            return Collections.singletonList(meetingService.create(uid, request));
        }
        throw new IllegalArgumentException("모임 생성 데이터가 맞지 않습니다. category: " + response.getCategory());
    }

    private List<CalendarEventDto> getSchedule(AIResponseDTO response) {
        if (response.getData() instanceof AIResponseDTO.SelectSchedule data) {
            String keyword = data.getKeyword();
            Long start = data.getStart();
            Long end = data.getEnd();
            return calendarEventService.getEventsByKeyword(start, end, keyword);
        }
        throw new IllegalArgumentException("일정 조회 요청 데이터가 잘못됐습니다. category: " + response.getCategory());
    }

    private List<CalendarEventDto> deleteSchedule(AIResponseDTO response)
        throws GeneralSecurityException, IOException {
        List<CalendarEventDto> calendarEventDtos = getSchedule(response);
        for (CalendarEventDto calendarEventDto : calendarEventDtos) {
            calendarEventService.delete(calendarEventDto.getId());
        }
        return calendarEventDtos;
    }

    public CalendarEventDto generateSchedule(AIResponseDTO response)
        throws GeneralSecurityException, IOException {
        if (response.getData() instanceof AIResponseDTO.GenerateSchedule data) {
            CreateEventReq createEventReq = new CreateEventReq();
            createEventReq.setTitle(data.getTitle());
            createEventReq.setDescription(data.getDescription());
            createEventReq.setAllDay(data.getAllDay());
            createEventReq.setStart(data.getStart());
            createEventReq.setEnd(data.getEnd());

            if (options != null && !options.isEmpty()) {
                createEventReq.setColor(options.get(SECURE_RANDOM.nextInt(options.size())));
            }
            return calendarEventService.create(createEventReq);
        }
        throw new IllegalArgumentException("데이터 형태가 맞지 않습니다.");
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomPrincipal) {
            return ((CustomPrincipal) auth.getPrincipal()).getUserId();
        }
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }
        throw new IllegalStateException("no authenticated user");
    }
}

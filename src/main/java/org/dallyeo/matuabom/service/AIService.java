package org.dallyeo.matuabom.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.dto.Request.AIRequestDTO;
import org.dallyeo.matuabom.dto.Response.AI.AIServerResponse; // 새로 만든 DTO import
import org.dallyeo.matuabom.dto.Response.AI.AIResponseDTO;
import org.dallyeo.matuabom.dto.Response.InputCategory;
import org.dallyeo.matuabom.dto.meeting.MeetingCreateRequest;
import org.dallyeo.matuabom.dto.meeting.MeetingRequirementDto;
import org.dallyeo.matuabom.repository.MeetingRepository;
import org.dallyeo.matuabom.security.CustomPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class AIService {

    private final MeetingService meetingService;
    private final MeetingRepository meetingRepository;
    private final WebClient webClient;
    private final CalendarEventService calendarEventService;

    List<String> options = List.of("bg-blue-500", "bg-purple-500", "bg-green-500", "bg-orange-500", "bg-pink-500");
    Random random = new Random();

    // 반환 타입을 ResponseEntity<AIResponse>로 변경
    public ResponseEntity<AIServerResponse> call(AIRequestDTO requestDTO)
        throws GeneralSecurityException, IOException {

        // 1. Python AI 서버 호출
        AIResponseDTO response = webClient.post()
            .uri("http://127.0.0.1:5000/main")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestDTO)
            .retrieve()
            .bodyToMono(AIResponseDTO.class)
            .block();

        // 2. 분류 및 결과 포장 후 반환
        return ResponseEntity.ok(classify(response));
    }

    // 반환 타입을 Object -> AIResponse로 변경
    public AIServerResponse classify(AIResponseDTO response)
        throws GeneralSecurityException, IOException {

        InputCategory category = response.getCategory();
        Object resultData = null;

        // 카테고리별 로직 수행
        if (category != null) {
            switch (category) {
                case 일정생성:
                    resultData = generateSchedule(response);
                    break;
                case 일정조회:
                    resultData = getSchedule(response);
                    break;
                case 일정삭제:
                    resultData = deleteSchedule(response);
                    break;
                case 모임생성:
                    resultData = generateMeeting(response);
                    break;
                case 모임조회:
                    resultData = getMeeting(response);
                    break;
                default:
                    // 정의되지 않은 카테고리 처리 (필요 시 에러 메시지 등)
                    break;
            }
        }
        String summary = "요청을 처리했습니다."; // 기본값
        if (resultData != null && category != null) {
            try {
                summary = fetchSummaryFromAI(category, resultData);
            } catch (Exception e) {
                System.err.println("요약 생성 실패: " + e.getMessage());
            }
        }

        // 3. 카테고리와 데이터를 묶어서 반환
        return AIServerResponse.builder()
            .category(category)
            .summary(summary)
            .data(resultData)
            .build();
    }
    private String fetchSummaryFromAI(InputCategory category, Object resultData) {
        // 요청 바디 생성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("category", category.name()); // Enum -> String
        requestBody.put("data", resultData);          // 실행 결과 객체

        // Python API 호출 (/summary)
        Map response = webClient.post()
            .uri("http://127.0.0.1:5000/summary")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        return (String) response.get("summary");
    }


    // 하위 비즈니스 로직 메서드들 (기존 로직 유지)
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

            if (data.getTimeConstraints() != null) {
                requirementDto.setTimeConstraints(data.getTimeConstraints());
            } else {
                requirementDto.setTimeConstraints(new ArrayList<>());
            }

            request.setName(data.getTitle());
            request.setInvitedUserIds(new ArrayList<>());
            request.setRequirement(requirementDto);
            request.setDefaultReflectCalendar(true);
            request.setDefaultReflectTimetable(true);

            String uid = userId();
            return Collections.singletonList(meetingService.create(uid, request));
        }
        throw new IllegalArgumentException("모임 생성 데이터 형식이 올바르지 않습니다. category: " + response.getCategory());
    }

    private List<CalendarEventDto> getSchedule(AIResponseDTO response) {
        if (response.getData() instanceof AIResponseDTO.SelectSchedule data) {
            String keyword = data.getKeyword();
            Long start = data.getStart();
            Long end = data.getEnd();
            return calendarEventService.getEventsByKeyword(start, end, keyword);
        }
        throw new IllegalArgumentException("일정 조회 요청 데이터가 아닙니다. category: " + response.getCategory());
    }

    private List<CalendarEventDto> deleteSchedule(AIResponseDTO response)
        throws GeneralSecurityException, IOException {
        List<CalendarEventDto> calendarEventDtos = getSchedule(response);
        for (CalendarEventDto calendarEventDto : calendarEventDtos) {
            String id = calendarEventDto.getId();
            calendarEventService.delete(id);
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
                createEventReq.setColor(options.get(random.nextInt(options.size())));
            }
            return calendarEventService.create(createEventReq);
        }
        throw new IllegalArgumentException("데이터 형식이 맞지 않습니다.");
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomPrincipal) {
            CustomPrincipal principal = (CustomPrincipal) auth.getPrincipal();
            return principal.getUserId();
        }
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }
        throw new IllegalStateException("no authenticated user");
    }
}

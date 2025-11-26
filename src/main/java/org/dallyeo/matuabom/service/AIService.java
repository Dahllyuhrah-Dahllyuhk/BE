package org.dallyeo.matuabom.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.dto.Request.AIRequestDTO;
import org.dallyeo.matuabom.dto.Response.AI.AIResponseDTO;
import org.dallyeo.matuabom.dto.Response.InputCategory;
import org.dallyeo.matuabom.dto.meeting.MeetingCreateRequest;
import org.dallyeo.matuabom.dto.meeting.MeetingRequirementDto;
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
    List<String> options = List.of("bg-blue-500", "bg-purple-500", "bg-green-500", "bg-orange-500", "bg-pink-500");
    Random random = new Random();

    private final WebClient webClient;
    private final CalendarEventService calendarEventService;

    public ResponseEntity<?> call(AIRequestDTO requestDTO)
        throws GeneralSecurityException, IOException {
        AIResponseDTO response = webClient.post()
            .uri("http://localhost:5000/main")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(requestDTO)
            .retrieve()
            .bodyToMono(AIResponseDTO.class)
            .block();

        return ResponseEntity.ok(classify(response));
    }

    public Object classify(AIResponseDTO response)
        throws GeneralSecurityException, IOException {
        InputCategory inputCategory = response.getCategory();

        if (Objects.requireNonNull(inputCategory) == InputCategory.일정생성) {
            return generateSchedule(response);
        }
        if (Objects.requireNonNull(inputCategory) == InputCategory.일정조회){
            return getSchedule(response);
        }
        if (Objects.requireNonNull(inputCategory) == InputCategory.일정삭제){
            return deleteSchedule(response);
        }
        if (Objects.requireNonNull(inputCategory) == InputCategory.모임생성){
            return generateMeeting(response);
        }
        if (Objects.requireNonNull(inputCategory) == InputCategory.모임조회){

        }
        
        return null;
    }

    private List<Meeting> generateMeeting(AIResponseDTO response){
        if (response.getData() instanceof AIResponseDTO.GenerateMeeting data) {

            MeetingCreateRequest request = new MeetingCreateRequest();
            MeetingRequirementDto requirementDto = new MeetingRequirementDto();

            requirementDto.setDateRangeStart(data.getDateRangeStart()); // yyyy-MM-dd
            requirementDto.setDateRangeEnd(data.getDateRangeEnd());     // yyyy-MM-dd
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

        // 1. AI 데이터 꺼내기
        if (response.getData() instanceof AIResponseDTO.GenerateSchedule data) {

            CreateEventReq createEventReq = new CreateEventReq();
            createEventReq.setTitle(data.getTitle());
            createEventReq.setDescription(data.getDescription());
            createEventReq.setAllDay(data.getAllDay());

            // 🔥 [핵심 수정] ISO 문자열("2025-11-30T...")을 타임스탬프("1764...")로 변환
            // 이걸 안 하면 서비스가 파싱 에러를 내고 "오늘 날짜"로 저장해버립니다.
            createEventReq.setStart(data.getStart());
            createEventReq.setEnd(data.getEnd());

            // 색상 랜덤
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
            return principal.getUserId(); // 정확한 String ID 반환 (예: "6918...")
        }

        // 혹시 모를 호환성 (Principal이 String인 경우)
        if (auth != null && auth.getPrincipal() instanceof String) {
            return (String) auth.getPrincipal();
        }

        throw new IllegalStateException("no authenticated user");
    }
}

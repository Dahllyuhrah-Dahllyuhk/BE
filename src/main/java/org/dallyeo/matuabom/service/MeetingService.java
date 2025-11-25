package org.dallyeo.matuabom.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.domain.User;
import org.dallyeo.matuabom.domain.meeting.*;
import org.dallyeo.matuabom.domain.meeting.ParticipantTimeStatus;
import org.dallyeo.matuabom.dto.CalendarEventDto;
import org.dallyeo.matuabom.dto.CreateEventReq;
import org.dallyeo.matuabom.dto.meeting.*;
import org.dallyeo.matuabom.repository.CalendarEventRepository;
import org.dallyeo.matuabom.repository.MeetingRepository;
import org.dallyeo.matuabom.repository.UserRepository;
import org.dallyeo.matuabom.service.meeting.AvailableTimeCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final AvailableTimeCalculator availableTimeCalculator;

    private final GoogleOAuthClientService googleOAuthClientService;
        private final GoogleCalendarService googleCalendarService;
        private final CalendarEventRepository calendarEventRepository;

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAILY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ----------------------------------------------------------------------------------
    // 1. 모임 생성 (CREATE)
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting create(String hostUserId, MeetingCreateRequest request) {

        MeetingRequirement requirement = buildRequirement(request.getRequirement());

        List<String> allUserIds = new ArrayList<>(request.getInvitedUserIds());
        if (!allUserIds.contains(hostUserId)) {
            allUserIds.add(0, hostUserId);
        }

        // ✔ 참가자 초기 reflect 설정은 request의 defaultReflectTimetable / defaultReflectCalendar
        List<MeetingParticipant> participants = buildParticipants(
                hostUserId,
                allUserIds,
                request.isDefaultReflectTimetable(),
                request.isDefaultReflectCalendar()
        );

        // ✔ 호스트의 시스템 반영 스케줄 계산
        participants.stream()
                .filter(p -> p.getUserId().equals(hostUserId))
                .findFirst()
                .ifPresent(hostParticipant -> recalculateParticipantSchedules(hostParticipant, requirement));

        Meeting meeting = Meeting.builder()
                .hostUserId(hostUserId)
                .name(request.getName())
                .status("PENDING")
                .requirement(requirement)
                .participants(participants)
                .build();

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 기본 조회
    // ----------------------------------------------------------------------------------
    public List<Meeting> findAllByUserId(String userId) {
        return meetingRepository.findAllByHostUserIdOrParticipantsUserId(userId, userId);
    }

    public Meeting findById(String meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
    }

    // ----------------------------------------------------------------------------------
    // 초대 수락
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting acceptMeetingInvitation(String userId, String meetingId) {

        Meeting meeting = findById(meetingId);

        MeetingParticipant participant = meeting.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new SecurityException("User is not invited to this meeting."));

        if ("PENDING".equals(participant.getStatus())) {

            participant.setStatus("ACCEPTED");

            MeetingRequirement requirement = meeting.getRequirement();
            recalculateParticipantSchedules(participant, requirement);
        }

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 3. 모임 수정 (UPDATE)
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting update(
            String hostUserId,
            String meetingId,
            MeetingUpdateRequest request
    ) {
        Meeting meeting = findById(meetingId);

        if (!meeting.getHostUserId().equals(hostUserId)) {
            throw new SecurityException("Only the host can update the meeting.");
        }

        MeetingRequirement newRequirement = buildRequirement(request.getRequirement());

        System.out.println("DEBUG: DTO isAllDay=" + request.getRequirement().getIsAllDay());
        System.out.println("DEBUG: Built Requirement isAllDay=" + newRequirement.getIsAllDay());

        // ✔ 모임(이름/요구사항) 업데이트
        meeting.setName(request.getName());
        meeting.setRequirement(newRequirement);

        // ✔ 참가자 전체 재구성 (반영 여부는 유지해야 하나, 초대 목록이 바뀔 수 있음)
        List<String> newUserIds = new ArrayList<>(request.getInvitedUserIds());
        if (!newUserIds.contains(hostUserId)) {
            newUserIds.add(0, hostUserId);
        }

        // ✔ 기존 reflectTimetable / reflectCalendar를 유지하는 방식으로 participants 업데이트
        Map<String, MeetingParticipant> oldParticipantMap =
                meeting.getParticipants().stream()
                        .collect(Collectors.toMap(MeetingParticipant::getUserId, p -> p));

        List<MeetingParticipant> updatedParticipants = newUserIds.stream()
                .map(id -> {
                    MeetingParticipant old = oldParticipantMap.get(id);
                    if (old != null) {
                        // 기존 참가자 → reflect 설정 유지
                        return old;
                    }
                    // 새 참가자 → 기본값(false, false)
                    User user = userRepository.findById(id).orElse(null);
                    MeetingParticipant p = new MeetingParticipant();
                    p.setUserId(id);
                    p.setName(user != null ? user.getNickname() : "Unknown User");
                    p.setStatus(id.equals(hostUserId) ? "ACCEPTED" : "PENDING");
                    p.setReflectTimetable(false);
                    p.setReflectCalendar(false);
                    p.setTimeStatuses(new ArrayList<>());
                    return p;
                })
                .collect(Collectors.toList());

        meeting.setParticipants(updatedParticipants);

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 삭제
    // ----------------------------------------------------------------------------------
    @Transactional
    public void delete(String hostUserId, String meetingId) {
        Meeting meeting = findById(meetingId);
        if (!meeting.getHostUserId().equals(hostUserId)) {
            throw new SecurityException("Only the host can delete the meeting.");
        }
        meetingRepository.delete(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 참가자 시간표 전체 업데이트 (PUT)
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting updateParticipantTimeStatus(
            String userId,
            String meetingId,
            List<ParticipantTimeStatus> timeStatuses
    ) {
        Meeting meeting = findById(meetingId);

        MeetingParticipant participant = meeting.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found..."));

        participant.setTimeStatuses(timeStatuses);

        MeetingRequirement requirement = meeting.getRequirement();
        recalculateParticipantSchedules(participant, requirement);

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 참가자 시간표 부분 업데이트 (PATCH)
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting patchParticipantTimeStatus(
            String userId,
            String meetingId,
            List<AvailabilitySlotUpdateDto> partialUpdates
    ) {
        Meeting meeting = findById(meetingId);

        MeetingParticipant participant = meeting.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + userId));

        if (partialUpdates.isEmpty()) return meeting;

        List<ParticipantTimeStatus> timeStatuses = participant.getTimeStatuses();
        if (timeStatuses == null) {
            timeStatuses = new ArrayList<>();
            participant.setTimeStatuses(timeStatuses);
        }

        LocalDate targetDate = LocalDate.parse(partialUpdates.get(0).getDate());

        Optional<ParticipantTimeStatus> targetStatusOpt = timeStatuses.stream()
                .filter(s -> s.getDate() != null && s.getDate().equals(targetDate))
                .findFirst();

        final ParticipantTimeStatus targetStatus;

        if (targetStatusOpt.isPresent()) {
            targetStatus = targetStatusOpt.get();
        } else {
            targetStatus = ParticipantTimeStatus.createEmpty(targetDate);
            timeStatuses.add(targetStatus);
        }

        Set<Integer> impossibleSlots = targetStatus.getImpossibleSlots();
        if (impossibleSlots == null) {
            impossibleSlots = new HashSet<>();
            targetStatus.setImpossibleSlots(impossibleSlots);
        }

        for (AvailabilitySlotUpdateDto update : partialUpdates) {
            Set<Integer> incomingSlots = new HashSet<>(update.getSlots());

            if ("POSSIBLE".equals(update.getStatus())) {
                impossibleSlots.removeAll(incomingSlots);
            } else if ("IMPOSSIBLE".equals(update.getStatus())) {
                impossibleSlots.addAll(incomingSlots);
            }
        }

        targetStatus.setImpossibleSlots(impossibleSlots);

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 참가자 개인 설정 업데이트 (reflectTimetable / reflectCalendar)
    // ----------------------------------------------------------------------------------
    @Transactional
    public Meeting updateParticipantSettings(
            String userId,
            String meetingId,
            boolean reflectTimetable,
            boolean reflectCalendar
    ) {
        Meeting meeting = findById(meetingId);

        meeting.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .ifPresentOrElse(
                        p -> {
                            p.setReflectTimetable(reflectTimetable);
                            p.setReflectCalendar(reflectCalendar);

                            MeetingRequirement requirement = meeting.getRequirement();
                            recalculateParticipantSchedules(p, requirement);
                        },
                        () -> { throw new IllegalArgumentException("Participant not found..."); }
                );

        return meetingRepository.save(meeting);
    }

    // ----------------------------------------------------------------------------------
    // 가용 시간 최종 계산
    // ----------------------------------------------------------------------------------
    public List<AvailableSlot> getFinalAvailableSlots(String meetingId) {
        Meeting meeting = findById(meetingId);
        return availableTimeCalculator.findCommonAvailableSlots(meeting.getParticipants());
    }

    public Map<String, DailyCountDto> getDailyAvailability(String meetingId) {
        Meeting meeting = findById(meetingId);

        List<LocalDate> candidateDates = availableTimeCalculator.expandDateRange(
                meeting.getRequirement().getDateRangeStart(),
                meeting.getRequirement().getDateRangeEnd()
        );

        int totalParticipants = meeting.getParticipants().size();

        Map<LocalDate, Long> availableCounts = candidateDates.stream()
                .collect(Collectors.toMap(
                        date -> date,
                        date -> meeting.getParticipants().stream()
                                .filter(p -> isParticipantAvailableOnDate(p, date))
                                .count()
                ));

        return availableCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().format(DAILY_DATE_FORMATTER),
                        entry -> new DailyCountDto(
                                entry.getKey().format(DAILY_DATE_FORMATTER),
                                totalParticipants,
                                entry.getValue().intValue()
                        )
                ));
    }

    // ----------------------------------------------------------------------------------
    // 유틸리티
    // ----------------------------------------------------------------------------------
    private void recalculateParticipantSchedules(MeetingParticipant participant, MeetingRequirement requirement) {

        List<ParticipantTimeStatus> statuses = new ArrayList<>();

        List<ParticipantTimeStatus> fixedImpossibleStatuses =
                availableTimeCalculator.calculateFixedImpossibleSlots(participant, requirement);

        statuses.addAll(fixedImpossibleStatuses);

        participant.setTimeStatuses(statuses);
    }

    private boolean isParticipantAvailableOnDate(MeetingParticipant participant, LocalDate date) {

        Optional<ParticipantTimeStatus> statusOpt = participant.getTimeStatuses().stream()
                .filter(s -> s.getDate() != null && s.getDate().equals(date))
                .findFirst();

        if (statusOpt.isEmpty()) {
            return true;
        }

        ParticipantTimeStatus status = statusOpt.get();
        Set<Integer> impossibleSlots = status.getImpossibleSlots();

        if (impossibleSlots == null) return true;

        return impossibleSlots.size() < 24;
    }

    private List<MeetingParticipant> buildParticipants(
            String hostId,
            List<String> userIds,
            boolean defaultReflectTimetable,
            boolean defaultReflectCalendar
    ) {
        return userIds.stream().map(id -> {
            User user = userRepository.findById(id).orElse(null);
            String name = (user != null) ? user.getNickname() : "Unknown User";

            MeetingParticipant p = new MeetingParticipant();
            p.setUserId(id);
            p.setName(name);
            p.setStatus(id.equals(hostId) ? "ACCEPTED" : "PENDING");
            p.setTimeStatuses(new ArrayList<>());
            p.setReflectTimetable(defaultReflectTimetable);
            p.setReflectCalendar(defaultReflectCalendar);

            return p;
        }).collect(Collectors.toList());
    }

    private MeetingRequirement buildRequirement(MeetingRequirementDto dto) {

        Instant start = LocalDate.parse(dto.getDateRangeStart(), DATE_FORMATTER)
                .atStartOfDay(ZONE_SEOUL).toInstant();

        Instant end = LocalDate.parse(dto.getDateRangeEnd(), DATE_FORMATTER)
                .atStartOfDay(ZONE_SEOUL).toInstant();

        List<TimeRange> constraints;

        if (dto.getIsAllDay()) {
            constraints = Collections.emptyList();
        } else if (dto.getTimeConstraints() != null) {
            constraints = dto.getTimeConstraints().stream()
                    .map(t -> new TimeRange(
                            LocalTime.parse(t.getStartTime()),
                            LocalTime.parse(t.getEndTime())
                    ))
                    .collect(Collectors.toList());
        } else {
            constraints = Collections.emptyList();
        }

        return MeetingRequirement.builder()
                .dateRangeStart(start)
                .dateRangeEnd(end)
                .isAllDay(dto.getIsAllDay())
                .timeConstraints(constraints)
                .build();
    }

    @Transactional
        public Meeting updateMeetingStatus(
                String hostUserId,
                String meetingId,
                MeetingStatusUpdateRequest request
        ) {
            Meeting meeting = findById(meetingId);

            if (!meeting.getHostUserId().equals(hostUserId)) {
                throw new SecurityException("Only the host can change meeting status.");
            }

            String newStatus = request.getStatus();
            if (newStatus == null ||
                    !(newStatus.equals("PENDING")
                            || newStatus.equals("CONFIRMED")
                            || newStatus.equals("CLOSED"))) {
                throw new IllegalArgumentException("Invalid status: " + newStatus);
            }

            String oldStatus = meeting.getStatus();

            // CONFIRMED일 때는 시간 필수
            if ("CONFIRMED".equals(newStatus)) {
                if (request.getConfirmedStart() == null || request.getConfirmedEnd() == null) {
                    throw new IllegalArgumentException("confirmedStart / confirmedEnd is required when status=CONFIRMED");
                }
                try {
                    Instant start = OffsetDateTime.parse(request.getConfirmedStart()).toInstant();
                    Instant end = OffsetDateTime.parse(request.getConfirmedEnd()).toInstant();
                    if (!end.isAfter(start)) {
                        throw new IllegalArgumentException("confirmedEnd must be after confirmedStart");
                    }
                    meeting.setConfirmedStart(start);
                    meeting.setConfirmedEnd(end);

                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid date format for confirmedStart/confirmedEnd", e);
                }

                // 📍 처음으로 CONFIRMED 되는 경우에만 이벤트 생성
                if (!"CONFIRMED".equals(oldStatus)) {
                    createConfirmedEventsForParticipants(meeting);
                }

            } else {
                // PENDING / CLOSED로 돌아가면 확정 시간 정보 제거
                meeting.setConfirmedStart(null);
                meeting.setConfirmedEnd(null);

                // 필요하다면: 과거에 생성된 meetingId 기반 calendar_events 삭제 가능
                // calendarEventRepository.deleteByMeetingId(meetingId);
            }

            meeting.setStatus(newStatus);
            return meetingRepository.save(meeting);
        }

        /**
         * 📍 모임이 CONFIRMED 되었을 때, 각 참가자의 calendar_events에 일정 하나씩 생성
         */
        private void createConfirmedEventsForParticipants(Meeting meeting) {
            Instant start = meeting.getConfirmedStart();
            Instant end = meeting.getConfirmedEnd();
            if (start == null || end == null) return;

            // 사람이 읽을 수 있는 ISO-8601 문자열 (Offset 포함, Asia/Seoul 기준)
            String startStr = start.atZone(ZONE_SEOUL).toOffsetDateTime().toString();
            String endStr = end.atZone(ZONE_SEOUL).toOffsetDateTime().toString();

            for (MeetingParticipant participant : meeting.getParticipants()) {
                // ACCEPTED 된 사람만 일정 생성
                if (!"ACCEPTED".equals(participant.getStatus())) continue;

                String uid = participant.getUserId();

                // 이벤트 요청 바디 생성
                CreateEventReq req = new CreateEventReq();
                req.setTitle(meeting.getName());           // 📍 요청했던 것: 모임 이름 그대로 제목
                req.setDescription(null);                  // 필요하면 "모임 확정 일정" 등 추가 가능
                req.setStart(startStr);
                req.setEnd(endStr);
                req.setAllDay(false);                      // 현재는 시간 단위 확정만 가정
                req.setTimeZone(ZONE_SEOUL.getId());
                req.setColor(null);                        // 색상 직접 안 주면 FE에서 처리

                try {
                    if (googleOAuthClientService.isLinked(uid)) {
                        // 구글 연동된 참가자 → 구글 + Mongo에 저장
                        GoogleOAuthClientEntity tokens = googleOAuthClientService
                                .getTokens(uid)
                                .orElseThrow(() -> new IllegalStateException("Google token not found for user: " + uid));

                        CalendarEventDto dto =
                                googleCalendarService.createGoogleEvent(tokens, uid, req);
                        googleCalendarService.attachMeetingId(dto, meeting.getId());

                    } else {
                        // 연동 안 된 참가자 → 로컬 Mongo에만 저장
                        CalendarEventDto dto =
                                googleCalendarService.createLocalEvent(uid, req);
                        googleCalendarService.attachMeetingId(dto, meeting.getId());
                    }
                } catch (GeneralSecurityException | IOException e) {
                    // 캘린더 연동 실패하더라도 모임 상태 저장은 진행
                    // 필요하면 로그만 찍고 계속
                    throw new IllegalStateException("Failed to create calendar event for user " + uid, e);
                }
            }
        }
}

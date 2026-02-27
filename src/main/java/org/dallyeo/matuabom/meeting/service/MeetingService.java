package org.dallyeo.matuabom.meeting.service;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.user.domain.UserEntity;
import org.dallyeo.matuabom.meeting.domain.*;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.dto.CreateEventReq;
import org.dallyeo.matuabom.meeting.dto.*;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleCalendarService;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.repository.jpa.UserJpaRepository;
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
    private final UserJpaRepository userRepository;
    private final AvailableTimeCalculator availableTimeCalculator;
    private final GoogleOAuthClientService googleOAuthClientService;
    private final GoogleCalendarService googleCalendarService;
    private final CalendarEventRepository calendarEventRepository;

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAILY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // -------------------------------------------------------------------------
    // 모임 생성
    // -------------------------------------------------------------------------

    @Transactional
    public Meeting create(String hostUserId, MeetingCreateRequest request) {
        MeetingRequirement requirement = buildRequirement(request.getRequirement());

        List<String> allUserIds = new ArrayList<>(request.getInvitedUserIds());
        if (!allUserIds.contains(hostUserId)) {
            allUserIds.add(0, hostUserId);
        }

        List<MeetingParticipant> participants = buildParticipants(
            hostUserId,
            allUserIds,
            request.isDefaultReflectTimetable(),
            request.isDefaultReflectCalendar()
        );

        // 호스트의 초기 불가능 슬롯 계산
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

    // -------------------------------------------------------------------------
    // 기본 조회
    // -------------------------------------------------------------------------

    public List<Meeting> findAllByUserId(String userId) {
        return meetingRepository.findAllByHostUserIdOrParticipantsUserId(userId, userId);
    }

    public Meeting findById(String meetingId) {
        return meetingRepository.findById(meetingId)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
    }

    // -------------------------------------------------------------------------
    // 초대 수락
    // -------------------------------------------------------------------------

    @Transactional
    public Meeting acceptMeetingInvitation(String userId, String meetingId) {
        Meeting meeting = findById(meetingId);

        MeetingParticipant participant = meeting.getParticipants().stream()
            .filter(p -> p.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new SecurityException("User is not invited to this meeting."));

        if ("PENDING".equals(participant.getStatus())) {
            participant.setStatus("ACCEPTED");
            recalculateParticipantSchedules(participant, meeting.getRequirement());
        }

        return meetingRepository.save(meeting);
    }

    // -------------------------------------------------------------------------
    // 모임 수정
    // -------------------------------------------------------------------------

    @Transactional
    public Meeting update(String hostUserId, String meetingId, MeetingUpdateRequest request) {
        Meeting meeting = findById(meetingId);

        if (!meeting.getHostUserId().equals(hostUserId)) {
            throw new SecurityException("Only the host can update the meeting.");
        }

        MeetingRequirement oldRequirement = meeting.getRequirement();
        MeetingRequirement newRequirement = buildRequirement(request.getRequirement());
        boolean requirementChanged = isRequirementChanged(oldRequirement, newRequirement);

        meeting.setName(request.getName());
        meeting.setRequirement(newRequirement);

        List<String> newUserIds = new ArrayList<>(request.getInvitedUserIds());
        if (!newUserIds.contains(hostUserId)) {
            newUserIds.add(0, hostUserId);
        }

        // 새 날짜 범위의 유효 날짜 집합
        Set<LocalDate> newValidDates = new HashSet<>(
            availableTimeCalculator.expandDateRange(
                newRequirement.getDateRangeStart(),
                newRequirement.getDateRangeEnd()
            )
        );

        Map<String, MeetingParticipant> oldParticipantMap = meeting.getParticipants().stream()
            .collect(Collectors.toMap(MeetingParticipant::getUserId, p -> p));

        List<MeetingParticipant> updatedParticipants = newUserIds.stream()
            .map(id -> {
                MeetingParticipant old = oldParticipantMap.get(id);
                if (old != null) {
                    if (requirementChanged) {
                        // requirement가 변경된 경우: 기존 슬롯을 새 범위에 맞게 조정 후 고정 슬롯 병합
                        mergeParticipantSchedules(old, newRequirement, newValidDates);
                    }
                    return old;
                }
                // 새로 추가된 참여자
                UserEntity user = userRepository.findByMongoId(id).orElse(null);
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

    /**
     * requirement 변경 시 참여자의 기존 슬롯을 보존하면서 새 범위에 맞게 조정.
     *
     * 처리 순서:
     * 1. 새 날짜 범위 밖의 timeStatuses 제거 (범위 밖 날짜는 의미 없음)
     * 2. 고정 불가 슬롯(캘린더/시간표) 재계산 → 기존 슬롯과 병합 (OR)
     *    - 사용자가 수동으로 설정한 불가 슬롯은 유지됨
     *    - 새로 계산된 고정 불가 슬롯이 추가됨
     *    - 단, 새 제약 조건 범위 밖의 슬롯은 고정 슬롯 계산에서 자연히 제외됨
     */
    private void mergeParticipantSchedules(
        MeetingParticipant participant,
        MeetingRequirement newRequirement,
        Set<LocalDate> newValidDates
    ) {
        // 1. 새 날짜 범위 밖의 기존 슬롯 제거
        List<ParticipantTimeStatus> trimmed = Optional.ofNullable(participant.getTimeStatuses())
            .orElse(Collections.emptyList())
            .stream()
            .filter(s -> s.getDate() != null && newValidDates.contains(s.getDate()))
            .collect(Collectors.toList());

        // 2. 새 requirement 기준으로 고정 불가 슬롯 재계산
        List<ParticipantTimeStatus> fixedSlots =
            availableTimeCalculator.calculateFixedImpossibleSlots(participant, newRequirement);

        // 3. 기존 슬롯 맵 생성 (날짜 → impossibleSlots)
        Map<LocalDate, Set<Integer>> mergedMap = new HashMap<>();
        for (ParticipantTimeStatus s : trimmed) {
            if (s.getImpossibleSlots() != null) {
                mergedMap.put(s.getDate(), new HashSet<>(s.getImpossibleSlots()));
            }
        }

        // 4. 고정 슬롯을 기존 슬롯에 병합 (합집합 — 어느 한쪽이라도 불가면 불가)
        for (ParticipantTimeStatus s : fixedSlots) {
            if (s.getImpossibleSlots() == null) continue;
            mergedMap.merge(
                s.getDate(),
                new HashSet<>(s.getImpossibleSlots()),
                (existing, incoming) -> {
                    existing.addAll(incoming);
                    return existing;
                }
            );
        }

        // 5. 결과를 participant에 반영
        List<ParticipantTimeStatus> result = mergedMap.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(e -> new ParticipantTimeStatus(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

        participant.setTimeStatuses(result);
    }

    /**
     * requirement의 날짜 범위 또는 시간 제약이 실질적으로 변경됐는지 확인.
     */
    private boolean isRequirementChanged(MeetingRequirement oldReq, MeetingRequirement newReq) {
        if (oldReq == null) return true;
        boolean dateChanged =
            !Objects.equals(oldReq.getDateRangeStart(), newReq.getDateRangeStart()) ||
            !Objects.equals(oldReq.getDateRangeEnd(),   newReq.getDateRangeEnd());
        boolean allDayChanged = oldReq.getIsAllDay() != newReq.getIsAllDay();
        boolean constraintsChanged = !Objects.equals(
            oldReq.getTimeConstraints(), newReq.getTimeConstraints()
        );
        return dateChanged || allDayChanged || constraintsChanged;
    }

    // -------------------------------------------------------------------------
    // 삭제
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(String hostUserId, String meetingId) {
        Meeting meeting = findById(meetingId);
        if (!meeting.getHostUserId().equals(hostUserId)) {
            throw new SecurityException("Only the host can delete the meeting.");
        }
        meetingRepository.delete(meeting);
    }

    // -------------------------------------------------------------------------
    // 참여자 시간 전체 업데이트 (PUT)
    // -------------------------------------------------------------------------

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
            .orElseThrow(() -> new IllegalArgumentException("Participant not found"));

        participant.setTimeStatuses(timeStatuses);
        recalculateParticipantSchedules(participant, meeting.getRequirement());

        return meetingRepository.save(meeting);
    }

    // -------------------------------------------------------------------------
    // 참여자 시간 부분 업데이트 (PATCH)
    //   - 모든 시간은 KST 기준으로 처리 (UTC 변환 없음)
    // -------------------------------------------------------------------------

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

        if (partialUpdates == null || partialUpdates.isEmpty()) {
            return meeting;
        }

        List<ParticipantTimeStatus> timeStatuses = participant.getTimeStatuses();
        if (timeStatuses == null) {
            timeStatuses = new ArrayList<>();
            participant.setTimeStatuses(timeStatuses);
        }

        AvailabilitySlotUpdateDto baseUpdate = partialUpdates.get(0);
        final List<ParticipantTimeStatus> finalTimeStatuses = timeStatuses;

        for (AvailabilitySlotUpdateDto update : partialUpdates) {
            // KST 기준 날짜 파싱 (UTC 변환 없이 그대로 사용)
            LocalDate localDate = LocalDate.parse(update.getDate());

            Optional<ParticipantTimeStatus> statusOpt = finalTimeStatuses.stream()
                .filter(s -> s.getDate().equals(localDate))
                .findFirst();

            ParticipantTimeStatus targetStatus;
            if (statusOpt.isPresent()) {
                targetStatus = statusOpt.get();
            } else {
                targetStatus = ParticipantTimeStatus.createEmpty(localDate);
                finalTimeStatuses.add(targetStatus);
            }

            Set<Integer> impossibleSlots = targetStatus.getImpossibleSlots();
            if (impossibleSlots == null) {
                impossibleSlots = new HashSet<>();
                targetStatus.setImpossibleSlots(impossibleSlots);
            }

            if ("IMPOSSIBLE".equals(update.getStatus())) {
                impossibleSlots.addAll(update.getSlots());
            } else if ("POSSIBLE".equals(update.getStatus())) {
                impossibleSlots.removeAll(update.getSlots());
            }
        }

        return meetingRepository.save(meeting);
    }

    // -------------------------------------------------------------------------
    // 참여자 설정 업데이트 (reflectTimetable / reflectCalendar)
    // -------------------------------------------------------------------------

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
                    recalculateParticipantSchedules(p, meeting.getRequirement());
                },
                () -> { throw new IllegalArgumentException("Participant not found"); }
            );

        return meetingRepository.save(meeting);
    }

    // -------------------------------------------------------------------------
    // 가용 시간 조회
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // 모임 상태 변경
    // -------------------------------------------------------------------------

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
        if (newStatus == null || !(newStatus.equals("PENDING")
            || newStatus.equals("CONFIRMED")
            || newStatus.equals("CLOSED"))) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        String oldStatus = meeting.getStatus();

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

            // 처음으로 CONFIRMED 되는 경우에만 캘린더 이벤트 생성
            if (!"CONFIRMED".equals(oldStatus)) {
                createConfirmedEventsForParticipants(meeting);
            }

        } else if ("PENDING".equals(newStatus)) {
            meeting.setConfirmedStart(null);
            meeting.setConfirmedEnd(null);
        }

        meeting.setStatus(newStatus);
        return meetingRepository.save(meeting);
    }

    /**
     * 모임 확정 시 ACCEPTED 참여자 전원의 캘린더에 이벤트 생성
     */
    private void createConfirmedEventsForParticipants(Meeting meeting) {
        Instant start = meeting.getConfirmedStart();
        Instant end = meeting.getConfirmedEnd();
        if (start == null || end == null) return;

        String startStr = start.atZone(ZONE_SEOUL).toOffsetDateTime().toString();
        String endStr = end.atZone(ZONE_SEOUL).toOffsetDateTime().toString();

        for (MeetingParticipant participant : meeting.getParticipants()) {
            if (!"ACCEPTED".equals(participant.getStatus())) continue;

            String uid = participant.getUserId();

            CreateEventReq req = new CreateEventReq();
            req.setTitle(meeting.getName());
            req.setDescription(null);
            req.setStart(startStr);
            req.setEnd(endStr);
            req.setAllDay(false);
            req.setTimeZone(ZONE_SEOUL.getId());
            req.setColor(null);

            try {
                if (googleOAuthClientService.isLinked(uid)) {
                    GoogleOAuthClientEntity tokens = googleOAuthClientService
                        .getTokens(uid)
                        .orElseThrow(() -> new IllegalStateException("Google token not found for user: " + uid));
                    CalendarEventDto dto = googleCalendarService.createGoogleEvent(tokens, uid, req);
                    googleCalendarService.attachMeetingId(dto, meeting.getId());
                } else {
                    CalendarEventDto dto = googleCalendarService.createLocalEvent(uid, req);
                    googleCalendarService.attachMeetingId(dto, meeting.getId());
                }
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalStateException("Failed to create calendar event for user " + uid, e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private void recalculateParticipantSchedules(MeetingParticipant participant, MeetingRequirement requirement) {
        List<ParticipantTimeStatus> fixedImpossibleStatuses =
            availableTimeCalculator.calculateFixedImpossibleSlots(participant, requirement);
        participant.setTimeStatuses(new ArrayList<>(fixedImpossibleStatuses));
    }

    private boolean isParticipantAvailableOnDate(MeetingParticipant participant, LocalDate date) {
        Optional<ParticipantTimeStatus> statusOpt = participant.getTimeStatuses().stream()
            .filter(s -> s.getDate() != null && s.getDate().equals(date))
            .findFirst();

        if (statusOpt.isEmpty()) return true;

        Set<Integer> impossibleSlots = statusOpt.get().getImpossibleSlots();
        return impossibleSlots == null || impossibleSlots.size() < 24;
    }

    private List<MeetingParticipant> buildParticipants(
        String hostId,
        List<String> userIds,
        boolean defaultReflectTimetable,
        boolean defaultReflectCalendar
    ) {
        return userIds.stream().map(id -> {
            UserEntity user = userRepository.findByMongoId(id).orElse(null);
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
}

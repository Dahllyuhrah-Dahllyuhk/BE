package org.dallyeo.matuabom.meeting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dallyeo.matuabom.calendar.domain.GoogleOAuthClientEntity;
import org.dallyeo.matuabom.user.domain.User;
import org.dallyeo.matuabom.meeting.domain.*;
import org.dallyeo.matuabom.calendar.dto.CalendarEventDto;
import org.dallyeo.matuabom.calendar.dto.CreateEventReq;
import org.dallyeo.matuabom.meeting.dto.*;
import org.dallyeo.matuabom.auth.service.GoogleOAuthClientService;
import org.dallyeo.matuabom.calendar.service.GoogleCalendarService;
import org.dallyeo.matuabom.sse.service.EventSseService;
import org.dallyeo.matuabom.calendar.repository.CalendarEventRepository;
import org.dallyeo.matuabom.meeting.repository.MeetingRepository;
import org.dallyeo.matuabom.user.repository.UserRepository;
import org.dallyeo.matuabom.timetable.domain.TimetableItem;
import org.dallyeo.matuabom.global.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.dao.OptimisticLockingFailureException;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final AvailableTimeCalculator availableTimeCalculator;
    private final GoogleOAuthClientService googleOAuthClientService;
    private final GoogleCalendarService googleCalendarService;
    private final CalendarEventRepository calendarEventRepository;
    private final EventSseService eventSseService;

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

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

        // 전체 참여자 일정 배치 재계산 (N+1 방지)
        recalculateAllParticipantSchedules(participants, requirement);

        String inviteCode = generateUniqueInviteCode();

        Meeting meeting = Meeting.builder()
            .hostUserId(hostUserId)
            .name(request.getName())
            .status("PENDING")
            .inviteCode(inviteCode)
            .requirement(requirement)
            .participants(participants)
            .build();

        Meeting saved = meetingRepository.save(meeting);

        // 초대된 사용자(호스트 제외)에게 SSE 알림
        saved.getParticipants().stream()
            .filter(p -> !p.getUserId().equals(hostUserId))
            .forEach(p -> eventSseService.sendMeetingInvited(
                p.getUserId(), saved.getId(), saved.getName()
            ));

        return saved;
    }

    // -------------------------------------------------------------------------
    // 기본 조회
    // -------------------------------------------------------------------------

    public List<Meeting> findAllByUserId(String userId) {
        return meetingRepository.findAllByHostUserIdOrParticipantsUserId(userId, userId);
    }

    public Meeting findById(String meetingId) {
        return meetingRepository.findById(meetingId)
            .orElseThrow(() -> NotFoundException.meeting(meetingId));
    }

    public boolean isParticipantOrHost(Meeting meeting, String userId) {
        if (userId.equals(meeting.getHostUserId())) return true;
        return meeting.getParticipants().stream().anyMatch(p -> userId.equals(p.getUserId()));
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
            .orElseThrow(() -> ForbiddenException.notMeetingMember());

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
        // 새로 추가된 참여자 일정 배치 재계산
        List<MeetingParticipant> newParticipants = updatedParticipants.stream()
            .filter(p -> !oldParticipantMap.containsKey(p.getUserId()))
            .collect(Collectors.toList());
        if (!newParticipants.isEmpty()) {
            recalculateAllParticipantSchedules(newParticipants, newRequirement);
        }
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
        // 모임에 연결된 CalendarEvent 고아 데이터 정리
        calendarEventRepository.deleteByMeetingId(meetingId);
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
            .orElseThrow(() -> NotFoundException.participant(userId));

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
            .orElseThrow(() -> NotFoundException.participant(userId));

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
                () -> { throw NotFoundException.participant("unknown"); }
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
        if (newStatus == null || MeetingStatus.from(newStatus) == null ||
            !(newStatus.equals(MeetingStatus.PENDING.name())
            || newStatus.equals(MeetingStatus.CONFIRMED.name())
            || newStatus.equals(MeetingStatus.CLOSED.name()))) {
            throw BadRequestException.invalidMeetingStatus(newStatus);
        }

        String oldStatus = meeting.getStatus();

        if ("CONFIRMED".equals(newStatus)) {
            if (request.getConfirmedStart() == null) {
                throw BadRequestException.confirmedStartRequired();
            }
            try {
                Instant start = OffsetDateTime.parse(request.getConfirmedStart()).toInstant();
                meeting.setConfirmedStart(start);

                if (request.getConfirmedEnd() != null) {
                    Instant end = OffsetDateTime.parse(request.getConfirmedEnd()).toInstant();
                    if (!end.isAfter(start)) {
                        throw BadRequestException.confirmedEndBeforeStart();
                    }
                    meeting.setConfirmedEnd(end);
                } else {
                    meeting.setConfirmedEnd(start.atZone(ZONE_SEOUL).toLocalDate()
                        .plusDays(1).atStartOfDay(ZONE_SEOUL).toInstant());
                }
            } catch (DateTimeParseException e) {
                throw BadRequestException.invalidDateFormat(e);
            }
        } else if ("PENDING".equals(newStatus)) {
            meeting.setConfirmedStart(null);
            meeting.setConfirmedEnd(null);
        }

        boolean shouldCreateEvents = "CONFIRMED".equals(newStatus) && !"CONFIRMED".equals(oldStatus);
        boolean shouldDeleteEvents = !"CONFIRMED".equals(newStatus) && "CONFIRMED".equals(oldStatus);
        meeting.setStatus(newStatus);
        Meeting saved = meetingRepository.save(meeting);

        if (shouldCreateEvents) {
            createConfirmedEventsForParticipants(saved);
            // 확정 알림: ACCEPTED 참여자 전원에게 SSE 발송
            saved.getParticipants().stream()
                .filter(p -> "ACCEPTED".equals(p.getStatus()))
                .forEach(p -> eventSseService.sendMeetingConfirmed(
                    p.getUserId(), saved.getId(), saved.getName()
                ));
        } else if (shouldDeleteEvents) {
            deleteConfirmedEventsForParticipants(saved);
        }

        return saved;
    }

    /**
     * 모임 확정 취소 시 ACCEPTED 참여자 전원의 캘린더에서 해당 모임 이벤트 삭제
     */
    private void deleteConfirmedEventsForParticipants(Meeting meeting) {
        List<CalendarEventDto> meetingEvents = calendarEventRepository.findByMeetingId(meeting.getId());
        if (meetingEvents.isEmpty()) return;

        for (CalendarEventDto event : meetingEvents) {
            String uid = event.getUserId();
            try {
                if (googleOAuthClientService.isLinked(uid)) {
                    GoogleOAuthClientEntity tokens = googleOAuthClientService.getTokens(uid).orElse(null);
                    if (tokens != null) {
                        googleCalendarService.deleteGoogleEvent(tokens, uid, event.getId());
                        continue;
                    }
                }
                // 구글 미연동이면 로컬 삭제
                calendarEventRepository.deleteById(event.getId());
            } catch (Exception e) {
                // 이미 삭제됐거나 실패해도 나머지는 계속 처리
                log.warn("Failed to delete calendar event {} for user {}: {}", event.getId(), uid, e.getMessage());
                calendarEventRepository.deleteById(event.getId());
            }
        }
    }

    /**
     * 모임 확정 시 ACCEPTED 참여자 전원의 캘린더에 이벤트 생성
     */
    private void createConfirmedEventsForParticipants(Meeting meeting) {
        Instant start = meeting.getConfirmedStart();
        Instant end = meeting.getConfirmedEnd();
        if (start == null || end == null) return;

        ZonedDateTime startZdt = start.atZone(ZONE_SEOUL);
        ZonedDateTime endZdt = end.atZone(ZONE_SEOUL);

        // 종일 여부: 시작이 자정이고 종료가 다음날 자정인 경우
        boolean isAllDay = startZdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
            && endZdt.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
            && !startZdt.toLocalDate().equals(endZdt.toLocalDate());

        // 종일이면 날짜 문자열("yyyy-MM-dd"), 비종일이면 UTC ISO 문자열("yyyy-MM-dd'T'HH:mm:ss'Z'")을 사용
        // toOffsetDateTime().toString()은 초를 생략("HH:mm+09:00")할 수 있어 createLocalEvent/buildGoogleEvent의
        // looksLikeDateOnly() 판별 실패 → LocalDate.now() fallback으로 오늘 날짜로 저장되는 버그 방지
        String startStr = isAllDay ? startZdt.toLocalDate().toString() : start.toString();
        String endStr   = isAllDay ? endZdt.toLocalDate().toString()   : end.toString();

        for (MeetingParticipant participant : meeting.getParticipants()) {
            if (!"ACCEPTED".equals(participant.getStatus())) continue;

            String uid = participant.getUserId();

            CreateEventReq req = new CreateEventReq();
            req.setTitle(meeting.getName());
            req.setDescription(null);
            req.setStart(startStr);
            req.setEnd(endStr);
            req.setAllDay(isAllDay);
            req.setTimeZone(ZONE_SEOUL.getId());
            req.setColor(null);

            try {
                if (googleOAuthClientService.isLinked(uid)) {
                    GoogleOAuthClientEntity tokens = googleOAuthClientService
                        .getTokens(uid)
                        .orElseThrow(() -> NotFoundException.googleToken(uid));
                    CalendarEventDto dto = googleCalendarService.createGoogleEvent(tokens, uid, req);
                    googleCalendarService.attachMeetingId(dto, meeting.getId());
                } else {
                    CalendarEventDto dto = googleCalendarService.createLocalEvent(uid, req);
                    googleCalendarService.attachMeetingId(dto, meeting.getId());
                }
            } catch (GeneralSecurityException | IOException e) {
                throw ExternalApiException.calendarEventCreateFailed(uid, e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private String generateUniqueInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
            String code = sb.toString();
            if (!meetingRepository.existsByInviteCode(code)) return code;
        }
        throw ConflictException.inviteCodeGenerationFailed();
    }

    // -------------------------------------------------------------------------
    // 모임 코드로 참여
    // -------------------------------------------------------------------------

    @Transactional
    public Meeting joinByInviteCode(String userId, String inviteCode) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Meeting meeting = meetingRepository.findByInviteCode(inviteCode.toUpperCase())
                    .orElseThrow(() -> NotFoundException.inviteCode(inviteCode));

                if (!"PENDING".equals(meeting.getStatus())) {
                    throw new IllegalStateException("조율 중인 모임에만 참여할 수 있습니다.");
                }

                boolean alreadyJoined = meeting.getParticipants().stream()
                    .anyMatch(p -> p.getUserId().equals(userId));
                if (alreadyJoined) {
                    throw new IllegalStateException("이미 참여한 모임입니다. meetingId=" + meeting.getId());
                }

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> NotFoundException.user(userId));

                MeetingParticipant newParticipant = new MeetingParticipant();
                newParticipant.setUserId(userId);
                newParticipant.setName(user.getNickname());
                newParticipant.setStatus("ACCEPTED");
                newParticipant.setTimeStatuses(new ArrayList<>());
                newParticipant.setReflectTimetable(true);
                newParticipant.setReflectCalendar(true);

                meeting.getParticipants().add(newParticipant);
                recalculateParticipantSchedules(newParticipant, meeting.getRequirement());
                Meeting saved = meetingRepository.save(meeting);

                // 호스트에게 참여 알림
                eventSseService.sendMeetingInvited(
                    saved.getHostUserId(), saved.getId(), saved.getName()
                );

                return saved;

            } catch (OptimisticLockingFailureException e) {
                if (attempt == maxRetries - 1) {
                    throw new IllegalStateException("모임 참여 중 충돌이 발생했습니다. 다시 시도해주세요.");
                }
            }
        }
        throw new IllegalStateException("모임 참여에 실패했습니다.");
    }

    private void recalculateParticipantSchedules(MeetingParticipant participant, MeetingRequirement requirement) {
        List<ParticipantTimeStatus> fixedImpossibleStatuses =
            availableTimeCalculator.calculateFixedImpossibleSlots(participant, requirement);
        participant.setTimeStatuses(new ArrayList<>(fixedImpossibleStatuses));
    }

    /**
     * 전체 참여자 일정 배치 재계산 — 캘린더/시간표를 userId 묶음으로 한 번씩만 조회하여 N+1 방지.
     */
    private void recalculateAllParticipantSchedules(List<MeetingParticipant> participants, MeetingRequirement requirement) {
        if (participants == null || participants.isEmpty()) return;

        List<LocalDate> candidateDates = availableTimeCalculator.expandDateRange(
            requirement.getDateRangeStart(), requirement.getDateRangeEnd());
        long startTs = candidateDates.get(0).atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();
        long endTs = candidateDates.get(candidateDates.size() - 1).plusDays(1).atStartOfDay(ZONE_SEOUL).toInstant().toEpochMilli();

        List<String> calendarUserIds = participants.stream()
            .filter(MeetingParticipant::isReflectCalendar)
            .map(MeetingParticipant::getUserId)
            .distinct()
            .collect(Collectors.toList());

        // 캘린더 이벤트 배치 조회 (N+1 방지)
        Map<String, List<CalendarEventDto>> calendarMap = calendarUserIds.isEmpty()
            ? Collections.emptyMap()
            : calendarEventRepository
                .findByUserIdInAndStartTimestampLessThanAndEndTimestampGreaterThan(calendarUserIds, endTs, startTs)
                .stream()
                .collect(Collectors.groupingBy(CalendarEventDto::getUserId));

        for (MeetingParticipant participant : participants) {
            List<CalendarEventDto> events = participant.isReflectCalendar()
                ? calendarMap.getOrDefault(participant.getUserId(), Collections.emptyList())
                : null;
            // 시간표는 개인별 조회 (배치 미지원) — 향후 최적화 가능
            List<TimetableItem> timetableItems = null;

            List<ParticipantTimeStatus> fixed =
                availableTimeCalculator.calculateFixedImpossibleSlots(participant, requirement, events, timetableItems);
            participant.setTimeStatuses(new ArrayList<>(fixed));
        }
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
}

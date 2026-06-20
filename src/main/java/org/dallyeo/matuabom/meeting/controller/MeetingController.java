package org.dallyeo.matuabom.meeting.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.meeting.domain.AvailableSlot;
import org.dallyeo.matuabom.meeting.domain.Meeting;
import org.dallyeo.matuabom.meeting.domain.ParticipantTimeStatus;
import org.dallyeo.matuabom.meeting.dto.*;
import org.dallyeo.matuabom.auth.security.CustomPrincipal;
import org.dallyeo.matuabom.meeting.service.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    // -------------------------------------------------------------------------
    // 모임 생성 및 조회
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<Meeting> createMeeting(
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody MeetingCreateRequest request
    ) {
        Meeting meeting = meetingService.create(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(meeting);
    }

    @GetMapping
    public ResponseEntity<List<Meeting>> getMyMeetings(
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        List<Meeting> meetings = meetingService.findAllByUserId(principal.getUserId());
        return ResponseEntity.ok(meetings);
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<Meeting> getMeetingDetail(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        Meeting meeting = meetingService.findById(meetingId);
        String userId = principal.getUserId();
        boolean isParticipant = meeting.getParticipants().stream()
                .anyMatch(p -> userId.equals(p.getUserId()));
        boolean isHost = userId.equals(meeting.getHostUserId());
        if (!isParticipant && !isHost) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(meeting);
    }

    // -------------------------------------------------------------------------
    // 모임 수정 및 삭제 (호스트 전용)
    // -------------------------------------------------------------------------

    @PutMapping("/{meetingId}")
    public ResponseEntity<Meeting> updateMeeting(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody MeetingUpdateRequest request
    ) {
        Meeting updatedMeeting = meetingService.update(principal.getUserId(), meetingId, request);
        return ResponseEntity.ok(updatedMeeting);
    }

    @DeleteMapping("/{meetingId}")
    public ResponseEntity<Void> deleteMeeting(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        meetingService.delete(principal.getUserId(), meetingId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // 참여자 액션
    // -------------------------------------------------------------------------

    @PostMapping("/{meetingId}/accept")
    public ResponseEntity<Meeting> acceptInvitation(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        Meeting updatedMeeting = meetingService.acceptMeetingInvitation(principal.getUserId(), meetingId);
        return ResponseEntity.ok(updatedMeeting);
    }

    /** 전체 시간 상태 덮어쓰기 (PUT) */
    @PutMapping("/{meetingId}/status")
    public ResponseEntity<Meeting> updateParticipantStatus(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody @NotNull List<ParticipantTimeStatus> timeStatuses
    ) {
        Meeting updatedMeeting = meetingService.updateParticipantTimeStatus(
            principal.getUserId(), meetingId, timeStatuses
        );
        return ResponseEntity.ok(updatedMeeting);
    }

    /** 시간 상태 부분 업데이트 (PATCH) */
    @PatchMapping("/{meetingId}/status")
    public ResponseEntity<Meeting> patchParticipantStatus(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody List<AvailabilitySlotUpdateDto> partialUpdates
    ) {
        Meeting updated = meetingService.patchParticipantTimeStatus(
            principal.getUserId(), meetingId, partialUpdates
        );
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{meetingId}/settings")
    public ResponseEntity<Meeting> updateParticipantSettings(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody ParticipantSettingsUpdateRequest request
    ) {
        Meeting updatedMeeting = meetingService.updateParticipantSettings(
            principal.getUserId(),
            meetingId,
            request.isReflectTimetable(),
            request.isReflectCalendar()
        );
        return ResponseEntity.ok(updatedMeeting);
    }

    // -------------------------------------------------------------------------
    // 가용 시간 조회
    // -------------------------------------------------------------------------

    @GetMapping("/{meetingId}/available-slots")
    public ResponseEntity<List<AvailableSlot>> getAvailableSlots(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        Meeting meeting = meetingService.findById(meetingId);
        if (!meetingService.isParticipantOrHost(meeting, principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(meetingService.getFinalAvailableSlots(meetingId));
    }

    // -------------------------------------------------------------------------
    // 모임 초대 코드
    // -------------------------------------------------------------------------

    /** 모임 초대 코드 조회 (호스트 전용) */
    @GetMapping("/{meetingId}/invite-code")
    public ResponseEntity<Map<String, String>> getMeetingInviteCode(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        Meeting meeting = meetingService.findById(meetingId);
        if (!meeting.getHostUserId().equals(principal.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(Map.of("inviteCode", meeting.getInviteCode() != null ? meeting.getInviteCode() : ""));
    }

    /** 초대 코드로 모임 참여 */
    @PostMapping("/join")
    public ResponseEntity<Meeting> joinByInviteCode(
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody JoinByInviteCodeRequest body
    ) {
        Meeting meeting = meetingService.joinByInviteCode(principal.getUserId(), body.getInviteCode().trim());
        return ResponseEntity.ok(meeting);
    }

    // -------------------------------------------------------------------------
    // 모임 상태 변경 (PENDING / CONFIRMED / CLOSED)
    // -------------------------------------------------------------------------

    @PatchMapping("/{meetingId}/state")
    public ResponseEntity<Meeting> updateMeetingState(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody MeetingStatusUpdateRequest request
    ) {
        Meeting updated = meetingService.updateMeetingStatus(
            principal.getUserId(), meetingId, request
        );
        return ResponseEntity.ok(updated);
    }
}

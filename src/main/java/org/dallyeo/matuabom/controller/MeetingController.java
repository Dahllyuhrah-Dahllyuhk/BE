package org.dallyeo.matuabom.controller;

import lombok.RequiredArgsConstructor;
import org.dallyeo.matuabom.domain.meeting.AvailableSlot;
import org.dallyeo.matuabom.domain.meeting.Meeting;
import org.dallyeo.matuabom.domain.meeting.ParticipantTimeStatus;
import org.dallyeo.matuabom.dto.meeting.DailyCountDto;
import org.dallyeo.matuabom.dto.meeting.MeetingCreateRequest;
import org.dallyeo.matuabom.dto.meeting.MeetingUpdateRequest;
import org.dallyeo.matuabom.dto.meeting.ParticipantSettingsUpdateRequest;
// ✨ DTO 임포트 (새로 생성한 슬롯 기반 DTO)
import org.dallyeo.matuabom.dto.meeting.AvailabilitySlotUpdateDto;

import org.dallyeo.matuabom.security.CustomPrincipal;
import org.dallyeo.matuabom.service.MeetingService;
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
    // 1. 모임 생성 및 조회 (CREATE & READ)
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
    public ResponseEntity<List<Meeting>> getMyMeetings(@AuthenticationPrincipal CustomPrincipal principal) {
        List<Meeting> meetings = meetingService.findAllByUserId(principal.getUserId());
        return ResponseEntity.ok(meetings);
    }

    @GetMapping("/{meetingId}")
    public ResponseEntity<Meeting> getMeetingDetail(@PathVariable String meetingId) {
        Meeting meeting = meetingService.findById(meetingId);
        return ResponseEntity.ok(meeting);
    }

    // -------------------------------------------------------------------------
    // 4. 모임 수정 및 삭제 (UPDATE & DELETE - HOST ONLY)
    // -------------------------------------------------------------------------

    @PutMapping("/{meetingId}")
    public ResponseEntity<Meeting> updateMeeting(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody MeetingUpdateRequest request
    ) {
        Meeting updatedMeeting = meetingService.update(
            principal.getUserId(),
            meetingId,
            request
        );
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
    // 5. 시간 응답 및 설정 (PARTICIPANT ACTIONS)
    // -------------------------------------------------------------------------

    @PostMapping("/{meetingId}/accept")
    public ResponseEntity<Meeting> acceptInvitation(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal
    ) {
        Meeting updatedMeeting = meetingService.acceptMeetingInvitation(principal.getUserId(), meetingId);
        return ResponseEntity.ok(updatedMeeting);
    }

    // PUT은 기존 방식 (전체 덮어쓰기)이므로 List<ParticipantTimeStatus>를 그대로 유지합니다.
    @PutMapping("/{meetingId}/status")
    public ResponseEntity<Meeting> updateParticipantStatus(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        @Valid @RequestBody @NotNull List<ParticipantTimeStatus> timeStatuses
    ) {
        Meeting updatedMeeting = meetingService.updateParticipantTimeStatus(
            principal.getUserId(),
            meetingId,
            timeStatuses
        );
        return ResponseEntity.ok(updatedMeeting);
    }

    /**
     * ✨ 수정: 슬롯 번호 기반의 요청을 받도록 타입을 변경합니다.
     * MeetingService의 시그니처도 List<AvailabilitySlotUpdateDto>를 받도록 변경해야 합니다.
     */
    @PatchMapping("/{meetingId}/status")
    public ResponseEntity<Meeting> patchParticipantStatus(
        @PathVariable String meetingId,
        @AuthenticationPrincipal CustomPrincipal principal,
        // ✨ 타입 변경: List<ParticipantTimeStatus> 대신 List<AvailabilitySlotUpdateDto>를 받습니다.
        @Valid @RequestBody List<AvailabilitySlotUpdateDto> partialUpdates
    ) {
        // MeetingService.java에서 이 타입으로 받을 수 있도록 함수 시그니처를 수정해야 합니다.
        Meeting updated = meetingService.patchParticipantTimeStatus(
            principal.getUserId(),
            meetingId,
            partialUpdates
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
    // 6. 가용 시간 계산 결과 조회 (READ CALCULATIONS)
    // -------------------------------------------------------------------------

    @GetMapping("/{meetingId}/available-slots")
    public ResponseEntity<List<AvailableSlot>> getAvailableSlots(@PathVariable String meetingId) {
        List<AvailableSlot> slots = meetingService.getFinalAvailableSlots(meetingId);
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/{meetingId}/daily-availability")
    public ResponseEntity<Map<String, DailyCountDto>> getDailyAvailability(@PathVariable String meetingId) {
        Map<String, DailyCountDto> dailyCounts = meetingService.getDailyAvailability(meetingId);
        return ResponseEntity.ok(dailyCounts);
    }

    // NOTE: deleteMeeting 함수에 return이 없어서 void로 변경했습니다. (위에서 수정됨)
}
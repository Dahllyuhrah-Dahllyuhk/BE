package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 404 — 리소스를 찾을 수 없음 */
public class NotFoundException extends AppException {

    public NotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }

    // ── 편의 팩토리 메서드 ────────────────────────────────────────────────────

    public static NotFoundException meeting(String meetingId) {
        return new NotFoundException("MEETING_NOT_FOUND", "모임을 찾을 수 없습니다: " + meetingId);
    }

    public static NotFoundException user(String userId) {
        return new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
    }

    public static NotFoundException calendarEvent(String eventId) {
        return new NotFoundException("CALENDAR_EVENT_NOT_FOUND", "캘린더 이벤트를 찾을 수 없습니다: " + eventId);
    }

    public static NotFoundException participant(String userId) {
        return new NotFoundException("PARTICIPANT_NOT_FOUND", "참여자를 찾을 수 없습니다.");
    }

    public static NotFoundException inviteCode(String code) {
        return new NotFoundException("INVITE_CODE_NOT_FOUND", "유효하지 않은 초대 코드입니다.");
    }

    public static NotFoundException timetable() {
        return new NotFoundException("TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다.");
    }

    public static NotFoundException timetableItem() {
        return new NotFoundException("TIMETABLE_ITEM_NOT_FOUND", "수업을 찾을 수 없습니다.");
    }

    public static NotFoundException friend() {
        return new NotFoundException("FRIEND_NOT_FOUND", "친구 정보를 찾을 수 없습니다.");
    }

    public static NotFoundException googleToken(String userId) {
        return new NotFoundException("GOOGLE_TOKEN_NOT_FOUND", "Google 토큰이 없습니다.");
    }
}

package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 400 — 잘못된 요청 */
public class BadRequestException extends AppException {

    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public BadRequestException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, errorCode, message, cause);
    }

    public static BadRequestException invalidMeetingStatus(String status) {
        return new BadRequestException("INVALID_MEETING_STATUS", "유효하지 않은 모임 상태입니다: " + status);
    }

    public static BadRequestException confirmedStartRequired() {
        return new BadRequestException("CONFIRMED_START_REQUIRED", "확정 상태로 변경 시 confirmedStart가 필요합니다.");
    }

    public static BadRequestException confirmedEndBeforeStart() {
        return new BadRequestException("CONFIRMED_END_BEFORE_START", "confirmedEnd는 confirmedStart 이후여야 합니다.");
    }

    public static BadRequestException invalidDateFormat(Throwable cause) {
        return new BadRequestException("INVALID_DATE_FORMAT", "날짜 형식이 올바르지 않습니다.", cause);
    }

    public static BadRequestException invalidTimeRange() {
        return new BadRequestException("INVALID_TIME_RANGE", "종료 시간이 시작 시간보다 늦어야 합니다.");
    }

    public static BadRequestException overlappingTimetable() {
        return new BadRequestException("OVERLAPPING_TIMETABLE", "시간표가 겹칩니다.");
    }

    public static BadRequestException invalidEncryptKey() {
        return new BadRequestException("INVALID_ENCRYPT_KEY", "암호화 키가 올바르지 않습니다. 32바이트(Base64 인코딩)여야 합니다.");
    }
}

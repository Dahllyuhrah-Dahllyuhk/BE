package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 502 — 외부 API 오류 (Google Calendar, AI 서버 등) */
public class ExternalApiException extends AppException {

    public ExternalApiException(String errorCode, String message) {
        super(HttpStatus.BAD_GATEWAY, errorCode, message);
    }

    public ExternalApiException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, errorCode, message, cause);
    }

    public static ExternalApiException googleRefreshFailed(Throwable cause) {
        return new ExternalApiException("GOOGLE_REFRESH_FAILED", "Google 토큰 갱신에 실패했습니다. 다시 연동해주세요.", cause);
    }

    public static ExternalApiException googleCalendarFailed(Throwable cause) {
        return new ExternalApiException("GOOGLE_CALENDAR_FAILED", "Google Calendar API 호출에 실패했습니다.", cause);
    }

    public static ExternalApiException calendarEventCreateFailed(String userId, Throwable cause) {
        return new ExternalApiException("CALENDAR_EVENT_CREATE_FAILED", "캘린더 이벤트 생성에 실패했습니다. userId: " + userId, cause);
    }
}

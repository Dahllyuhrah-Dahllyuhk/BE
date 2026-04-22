package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 403 — 권한 없음 */
public class ForbiddenException extends AppException {

    public ForbiddenException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }

    public static ForbiddenException notMeetingMember() {
        return new ForbiddenException("NOT_MEETING_MEMBER", "해당 모임의 참여자가 아닙니다.");
    }

    public static ForbiddenException notResourceOwner() {
        return new ForbiddenException("NOT_RESOURCE_OWNER", "리소스에 대한 권한이 없습니다.");
    }
}

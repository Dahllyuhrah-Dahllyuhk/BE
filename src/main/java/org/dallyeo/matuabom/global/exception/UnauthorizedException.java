package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 401 — 인증되지 않음 */
public class UnauthorizedException extends AppException {

    public UnauthorizedException(String errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }

    public static UnauthorizedException notAuthenticated() {
        return new UnauthorizedException("NOT_AUTHENTICATED", "인증된 사용자가 아닙니다.");
    }
}

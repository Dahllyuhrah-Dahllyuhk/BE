package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 최상위 예외.
 * 모든 도메인 예외는 이 클래스를 상속한다.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AppException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public AppException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

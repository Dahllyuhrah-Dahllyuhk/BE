package org.dallyeo.matuabom.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Custom AppException 계층 ──────────────────────────────────────────────

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, String>> handleAppException(AppException e) {
        HttpStatus status = e.getStatus();
        if (status.is5xxServerError()) {
            log.error("AppException [{}]: {}", e.getErrorCode(), e.getMessage(), e);
        } else {
            log.warn("AppException [{}]: {}", e.getErrorCode(), e.getMessage());
        }
        return ResponseEntity.status(status)
                .body(Map.of("error", e.getErrorCode(), "message", e.getMessage()));
    }

    // ── 레거시 예외 (AppException으로 교체되지 않은 잔여분) ──────────────────

    // 400 — @Valid 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("유효하지 않은 요청입니다.");
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(Map.of("error", "validation_failed", "message", message));
    }

    // 400 — 필수 파라미터 누락 / 파라미터 타입 불일치 (기존엔 catch-all 500로 빠지던 것)
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleBadRequestParams(Exception e) {
        log.warn("Bad request parameter: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "message", "요청 파라미터가 올바르지 않습니다."));
    }

    // 404 — 정적 리소스 미존재 (/favicon.ico, /robots.txt, /login 등 — 기존엔 500 노이즈)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
        log.debug("No static resource: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", "리소스를 찾을 수 없습니다."));
    }

    // 400 — 아직 교체 안 된 IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Bad request (legacy): {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_request", "message", e.getMessage()));
    }

    // 403 — 아직 교체 안 된 SecurityException
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        log.warn("Forbidden (legacy): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "forbidden", "message", e.getMessage()));
    }

    // 404 — Spring Security UsernameNotFoundException
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UsernameNotFoundException e) {
        log.warn("User not found (legacy): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    // 409 — 아직 교체 안 된 IllegalStateException
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        log.warn("Conflict (legacy): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "conflict", "message", e.getMessage()));
    }

    // 500 — 예상치 못한 예외 (스택 트레이스 외부 노출 차단)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            log.debug("Exception in SSE connection (suppressed): {}", e.getMessage());
            return null;
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_server_error", "message", "서버 오류가 발생했습니다."));
    }
}

package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 401 — 인증 실패 */
public class AuthException extends AppException {

    public AuthException(String errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }

    public static AuthException noAuthenticatedUser() {
        return new AuthException("NO_AUTHENTICATED_USER", "인증된 사용자가 없습니다.");
    }

    public static AuthException googleAccountNotLinked() {
        return new AuthException("GOOGLE_ACCOUNT_NOT_LINKED", "Google 계정이 연동되어 있지 않습니다.");
    }

    public static AuthException googleRefreshTokenMissing() {
        return new AuthException("GOOGLE_NO_REFRESH_TOKEN", "Google Refresh Token이 없습니다. 다시 로그인해주세요.");
    }

    public static AuthException googleTokenExpired() {
        return new AuthException("GOOGLE_TOKEN_EXPIRED", "Google 토큰이 만료되었습니다. 다시 연동해주세요.");
    }
}

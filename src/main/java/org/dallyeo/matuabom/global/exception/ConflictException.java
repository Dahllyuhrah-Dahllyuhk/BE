package org.dallyeo.matuabom.global.exception;

import org.springframework.http.HttpStatus;

/** 409 — 상태 충돌 */
public class ConflictException extends AppException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }

    public static ConflictException alreadyFriend() {
        return new ConflictException("ALREADY_FRIEND", "이미 친구입니다.");
    }

    public static ConflictException selfFriend() {
        return new ConflictException("SELF_FRIEND_NOT_ALLOWED", "자신과 친구가 될 수 없습니다.");
    }

    public static ConflictException selfInviteCode() {
        return new ConflictException("SELF_INVITE_CODE_NOT_ALLOWED", "자신의 초대코드는 사용할 수 없습니다.");
    }

    public static ConflictException inviteCodeGenerationFailed() {
        return new ConflictException("INVITE_CODE_GENERATION_FAILED", "초대코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    public static ConflictException googleEventAlreadyRemoved() {
        return new ConflictException("GOOGLE_EVENT_ALREADY_REMOVED", "이미 삭제된 Google 이벤트입니다.");
    }
}

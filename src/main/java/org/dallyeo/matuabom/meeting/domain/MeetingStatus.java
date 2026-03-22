package org.dallyeo.matuabom.meeting.domain;

/**
 * 모임 상태 Enum.
 * MongoDB 저장 시 문자열로 유지되므로 @Field 없이도 name()으로 직렬화됨.
 */
public enum MeetingStatus {
    PENDING,    // 조율 중
    CONFIRMED,  // 확정 완료
    CLOSED;     // 종료/마감

    public static MeetingStatus from(String value) {
        if (value == null) return PENDING;
        return switch (value.toUpperCase()) {
            case "CONFIRMED" -> CONFIRMED;
            case "CLOSED"    -> CLOSED;
            default          -> PENDING;
        };
    }
}

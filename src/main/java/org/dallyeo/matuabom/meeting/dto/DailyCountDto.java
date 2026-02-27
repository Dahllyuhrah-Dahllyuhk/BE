package org.dallyeo.matuabom.meeting.dto;

import lombok.Value;

/**
 * 모임 날짜별 참여 가능 인원수 조회 결과
 */
@Value
public class DailyCountDto {
    String date;               // yyyy-MM-dd
    int totalParticipants;     // 전체 참여자 수
    int availableParticipants; // 해당 날짜에 1시간 이상 가능한 참여자 수
}

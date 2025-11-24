package org.dallyeo.matuabom.dto.meeting;

import lombok.Value;

/**
 * 모임 캘린더 월별 뷰를 위한 날짜별 가용 인원 집계 결과
 */
@Value
public class DailyCountDto {
    private String date; // yyyy-MM-dd
    private int totalParticipants; // 전체 참여자 수
    private int availableParticipants; // 해당 날짜에 1시간 이상 가능한 참여자 수
}

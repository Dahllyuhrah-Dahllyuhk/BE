package org.dallyeo.matuabom.domain.meeting;

import lombok.Value;
import java.time.ZonedDateTime;

/**
 * 캘린더 이벤트, 시간표, 모임 제약 조건 등
 * 모든 고정되거나 기준이 되는 시간을 표현하기 위한 불변 객체.
 * ZonedDateTime을 사용하여 Timezone 처리를 명확히 합니다.
 */
@Value // ✨ 불변(Immutable) 객체로 정의 (Getter와 AllArgsConstructor만 제공)
public class FixedSchedule {
    private ZonedDateTime start; // 시작 시간 (Asia/Seoul Timezone 포함)
    private ZonedDateTime end;   // 종료 시간 (Asia/Seoul Timezone 포함)
    private boolean isAllDay;    // 종일 일정 여부 (계산 시 필요)
}
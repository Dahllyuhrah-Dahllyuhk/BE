package org.dallyeo.matuabom.domain.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingRequirement {

    private Instant dateRangeStart;          // 후보 날짜 시작
    private Instant dateRangeEnd;            // 후보 날짜 끝

    // 💡 종일 여부 플래그
    private Boolean isAllDay;

    // 💡 시간 구간 리스트: isAllDay가 false일 때만 사용됨
    private List<TimeRange> timeConstraints;
}
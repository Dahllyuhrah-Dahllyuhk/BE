package org.dallyeo.matuabom.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DashboardStats {
    private long upcomingCount;          // 예정된 미래 모임 수
    private int thisMonthMeetingCount;   // 이번 달 참여 모임 수
    private String topTimeSlot;
    private List<TimeSlotStatDto> timeSlotStats; // 시간대별 모임 횟수
}

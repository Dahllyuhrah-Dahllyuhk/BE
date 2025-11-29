package org.dallyeo.matuabom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DashboardStats {
    private long upcomingCount; //조율 중 모임 수
    private int thisMonthMeetingCount;
    private String topTimeSlot;
    private List<TimeSlotStatDto> timeSlotStats; //시간대 별 모임 횟수
}

package org.dallyeo.matuabom.domain.meeting;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;

// MongoDB에 내장될 시간 객체
@Data @NoArgsConstructor @AllArgsConstructor
public class TimeRange {
    private LocalTime startTime;
    private LocalTime endTime;
}

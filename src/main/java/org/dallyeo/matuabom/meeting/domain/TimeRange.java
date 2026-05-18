package org.dallyeo.matuabom.meeting.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeRange {
    private LocalTime startTime;
    private LocalTime endTime;
}

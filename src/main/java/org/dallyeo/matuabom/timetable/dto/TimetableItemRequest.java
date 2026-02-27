package org.dallyeo.matuabom.timetable.dto;

import lombok.Data;
import java.time.DayOfWeek;

@Data
public class TimetableItemRequest {
    private String title;
    private String professor;
    private DayOfWeek day;     // "MONDAY", "TUESDAY" ...
    private String startTime;  // "09:00"
    private String endTime;    // "10:30"
    private String color;
}
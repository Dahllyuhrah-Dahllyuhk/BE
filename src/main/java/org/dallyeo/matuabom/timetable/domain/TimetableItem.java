package org.dallyeo.matuabom.timetable.domain;

import lombok.*;
import java.time.DayOfWeek;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TimetableItem {
    private String id;

    private String title;
    private String color;

    private DayOfWeek day;

    private String startTime;
    private String endTime;
}

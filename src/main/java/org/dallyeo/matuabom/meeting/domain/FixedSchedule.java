package org.dallyeo.matuabom.meeting.domain;

import lombok.Value;
import java.time.ZonedDateTime;

@Value
public class FixedSchedule {
    ZonedDateTime start;
    ZonedDateTime end;
    boolean isAllDay;
}

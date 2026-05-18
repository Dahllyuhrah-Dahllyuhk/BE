package org.dallyeo.matuabom.meeting.domain;

import lombok.Value;
import java.time.Instant;

@Value
public class AvailableSlot {
    Instant start;
    Instant end;
}

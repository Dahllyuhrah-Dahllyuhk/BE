package org.dallyeo.matuabom.meeting.domain;

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

    private Instant dateRangeStart;
    private Instant dateRangeEnd;

    private Boolean isAllDay;

    private List<TimeRange> timeConstraints;
}

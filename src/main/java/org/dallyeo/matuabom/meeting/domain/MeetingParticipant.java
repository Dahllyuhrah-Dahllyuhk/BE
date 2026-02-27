package org.dallyeo.matuabom.meeting.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipant {
    private String userId;
    private String name;
    private String status; // ACCEPTED, PENDING 등 모임 전체 상태

    private List<ParticipantTimeStatus> timeStatuses;
    private boolean reflectTimetable;
    private boolean reflectCalendar;
}

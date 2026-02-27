package org.dallyeo.matuabom.meeting.dto;

import lombok.Data;

@Data
public class MeetingStatusUpdateRequest {
    private String status;         // PENDING / CONFIRMED / CLOSED
    private String confirmedStart;
    private String confirmedEnd;
}
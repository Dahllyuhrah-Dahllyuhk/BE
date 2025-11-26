package org.dallyeo.matuabom.dto.meeting;

import lombok.Data;

@Data
public class MeetingStatusUpdateRequest {
    private String status;         // PENDING / CONFIRMED / CLOSED
    private String confirmedStart; // ISO-8601 (Offset 포함)
    private String confirmedEnd;   // ISO-8601 (Offset 포함)
}

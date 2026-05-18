package org.dallyeo.matuabom.calendar.dto;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "calendar_events")
public class CalendarEventDto {

    @Id
    private String id;          // Google Calendar event ID

    private String userId;   //

    private String title;       // summary
    private String start;
    private String end;
    private boolean allDay;
    private Long startTimestamp;
    private Long endTimestamp;
    private String description;

    private String timeZone;    // Asia/Seoul

    private String color;

    private String meetingId;
}
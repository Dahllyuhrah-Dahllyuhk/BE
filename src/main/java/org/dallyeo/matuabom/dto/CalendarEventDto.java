package org.dallyeo.matuabom.dto;

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

    private String userEmail;   // 이벤트 주인의 이메일 (여러 사용자 지원 대비)

    private String title;       // summary
    private String start;       // ISO-8601 날짜/시간 (string)
    private String end;         // ISO-8601 날짜/시간 (string)
    private boolean allDay;     // allday 여부
    private Long startTimestamp; // timestamp (정렬/검색용)
    private Long endTimestamp;
    private String description;

    private String timeZone;    // Asia/Seoul

    private String color;
}

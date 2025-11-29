package org.dallyeo.matuabom.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEventReq {
    private String title;
    private String description;
    /** allDay = true이면 yyyy-MM-dd, 아니면 ISO-8601(오프셋 포함) */
    private String start;
    /** allDay = true이면 yyyy-MM-dd(구글은 exclusive end), 아니면 ISO-8601 */
    private String end;
    private Boolean allDay;    // null이면 서버가 start/end 형식으로 추정
    private String timeZone;   // 없으면 Asia/Seoul 사용

    private String color;
}

package org.dallyeo.matuabom.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CreateEventReq {
    private String title;
    private String start;       // ISO or yyyy-MM-dd
    private String end;         // ISO or yyyy-MM-dd
    private Boolean allDay;     // null 허용 (기본 false)
    private String description; // 선택
}

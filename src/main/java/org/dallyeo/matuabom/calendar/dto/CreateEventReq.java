package org.dallyeo.matuabom.calendar.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateEventReq {
    private String title;
    private String description;
    /** allDay = true?대㈃ yyyy-MM-dd, ?꾨땲硫?ISO-8601(?ㅽ봽???ы븿) */
    private String start;
    /** allDay = true?대㈃ yyyy-MM-dd(援ш?? exclusive end), ?꾨땲硫?ISO-8601 */
    private String end;
    private Boolean allDay;
    private String timeZone;

    private String color;
}
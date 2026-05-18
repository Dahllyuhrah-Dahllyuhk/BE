package org.dallyeo.matuabom.meeting.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;

@Data
public class TimeRangeDto {
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "?쒓컙 ?뺤떇? HH:mm?댁뼱???⑸땲??")
    private String startTime;
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "?쒓컙 ?뺤떇? HH:mm?댁뼱???⑸땲??")
    private String endTime;
}
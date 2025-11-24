package org.dallyeo.matuabom.dto.meeting;

import lombok.Data;
import jakarta.validation.constraints.Pattern;

@Data
public class TimeRangeDto {
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "시간 형식은 HH:mm이어야 합니다.")
    private String startTime;
    @Pattern(regexp = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$", message = "시간 형식은 HH:mm이어야 합니다.")
    private String endTime;
}

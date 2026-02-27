package org.dallyeo.matuabom.meeting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
public class MeetingRequirementDto {
    @NotBlank
    private String dateRangeStart; // yyyy-MM-dd
    @NotBlank
    private String dateRangeEnd;   // yyyy-MM-dd

    @JsonProperty("isAllDay")
    private Boolean isAllDay;

    private List<TimeRangeDto> timeConstraints;
}

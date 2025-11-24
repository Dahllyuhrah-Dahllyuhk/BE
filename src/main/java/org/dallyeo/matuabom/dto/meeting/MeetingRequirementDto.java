package org.dallyeo.matuabom.dto.meeting;

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

    // 💡 종일 여부
    @JsonProperty("isAllDay")
    private Boolean isAllDay;

    // 💡 시간 제약 (isAllDay가 false일 때만 사용)
    private List<TimeRangeDto> timeConstraints;
}

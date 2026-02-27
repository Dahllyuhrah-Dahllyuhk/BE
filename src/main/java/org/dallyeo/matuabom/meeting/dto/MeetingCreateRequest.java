package org.dallyeo.matuabom.meeting.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class MeetingCreateRequest {
    @NotBlank
    private String name;

    @Valid
    private MeetingRequirementDto requirement;

    @NotNull
    private List<String> invitedUserIds;

    private boolean defaultReflectTimetable;
    private boolean defaultReflectCalendar;
}

package org.dallyeo.matuabom.meeting.dto;

import lombok.Value;

import jakarta.validation.constraints.NotNull;

@Value
public class ParticipantSettingsUpdateRequest {
    @NotNull
    boolean reflectTimetable;

    @NotNull
    boolean reflectCalendar;
}
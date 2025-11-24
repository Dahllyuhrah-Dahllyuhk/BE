package org.dallyeo.matuabom.dto.meeting;

import lombok.Value;

import jakarta.validation.constraints.NotNull;

@Value
public class ParticipantSettingsUpdateRequest {
    @NotNull
    boolean reflectTimetable; // 주간 시간표 반영 여부

    @NotNull
    boolean reflectCalendar; // 캘린더 일정 반영 여부
}
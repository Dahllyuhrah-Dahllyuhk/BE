package org.dallyeo.matuabom.domain.meeting;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingParticipant {
    private String userId;
    private String name;
    private String status; // ACCEPTED, PENDING 등 모임 전체 상태

    // ✨ 사용자가 모임 페이지에서 직접 입력/수정한 시간대별 응답 상태
    private List<ParticipantTimeStatus> timeStatuses;
    private boolean reflectTimetable;
    private boolean reflectCalendar;
}
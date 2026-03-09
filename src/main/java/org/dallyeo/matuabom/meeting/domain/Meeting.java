package org.dallyeo.matuabom.meeting.domain;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "meetings")
public class Meeting {
    @Id
    private String id;
    private String hostUserId;
    private String name;
    private String inviteCode; // 모임 초대 코드 (8자리)
    /**
     * 상태:
     * - PENDING   : 투표/조율 중
     * - CONFIRMED : 확정 완료
     * - CLOSED    : 종료/마감
     */
    private String status;

    private Instant confirmedStart;
    private Instant confirmedEnd;

    private MeetingRequirement requirement;
    private List<MeetingParticipant> participants;

    @Builder.Default
    private Instant createdAt = Instant.now();
}

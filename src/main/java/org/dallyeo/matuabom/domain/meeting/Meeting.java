package org.dallyeo.matuabom.domain.meeting;

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
    private List<MeetingParticipant> participants; // 참여자 목록

    // 필드 초기화 시, @Builder의 동작을 위해 final이 아닌 일반 필드로 유지하거나,
    // @Builder.Default를 사용해야 하지만, 현재는 일반 필드이므로 @Builder, @AllArgsConstructor 조합으로 처리됩니다.
    @Builder.Default
    private Instant createdAt = Instant.now();
}
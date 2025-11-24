package org.dallyeo.matuabom.domain.meeting;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

// 참여자 개개인이 특정 시간대에 응답한 상태 (슬롯 번호 기반)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantTimeStatus {

    // 이 상태가 적용되는 날짜 (UTC 기준)
    private LocalDate date;

    // 해당 날짜의 불가능(IMPOSSIBLE)한 1시간 단위 슬롯 번호 집합 (0~23)
    private Set<Integer> impossibleSlots;

    // NOTE: 이제 저장된 슬롯은 항상 IMPOSSIBLE을 의미합니다.
    private String status;

    // 편의상 초기화 생성자 추가 (status는 IMPOSSIBLE로 고정)
    public ParticipantTimeStatus(LocalDate date, Set<Integer> impossibleSlots) {
        this.date = date;
        this.impossibleSlots = impossibleSlots;
        this.status = "IMPOSSIBLE";
    }

    public static ParticipantTimeStatus createEmpty(LocalDate date) {
        return new ParticipantTimeStatus(date, new HashSet<>(), "IMPOSSIBLE");
    }
}
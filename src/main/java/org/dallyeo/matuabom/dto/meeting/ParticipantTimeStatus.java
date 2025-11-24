package org.dallyeo.matuabom.dto.meeting;

import lombok.Data;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Data
public class ParticipantTimeStatus {
    private LocalDate date;          // YYYY-MM-DD
    private Set<Integer> impossibleSlots; // 그 날짜에서 "불가능한" 시간 슬롯 번호 (0~23)

    public static ParticipantTimeStatus createEmpty(LocalDate date) {
        ParticipantTimeStatus s = new ParticipantTimeStatus();
        s.setDate(date);
        s.setImpossibleSlots(new HashSet<>());
        return s;
    }
}
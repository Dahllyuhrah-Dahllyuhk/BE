package org.dallyeo.matuabom.meeting.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantTimeStatus {

    private LocalDate date;

    private Set<Integer> impossibleSlots;

    private String status;

    public ParticipantTimeStatus(LocalDate date, Set<Integer> impossibleSlots) {
        this.date = date;
        this.impossibleSlots = impossibleSlots;
        this.status = "IMPOSSIBLE";
    }

    public static ParticipantTimeStatus createEmpty(LocalDate date) {
        return new ParticipantTimeStatus(date, new HashSet<>(), "IMPOSSIBLE");
    }
}

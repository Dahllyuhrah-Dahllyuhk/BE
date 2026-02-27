package org.dallyeo.matuabom.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotUpdateDto {

    @NotNull
    private String date;

    @NotNull
    private List<Integer> slots;

    @NotNull
    private String status;
}

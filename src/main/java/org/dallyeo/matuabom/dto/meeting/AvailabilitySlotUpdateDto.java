package org.dallyeo.matuabom.dto.meeting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 프론트엔드에서 PATCH /api/meetings/{meetingId}/status 요청 시
 * 슬롯 번호(0-23) 기반으로 시간 상태를 업데이트하기 위해 사용되는 DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotUpdateDto {

    @NotNull
    private String date; // "YYYY-MM-DD" 형태의 날짜

    @NotNull
    private List<Integer> slots; // 1시간 단위 슬롯 번호 (0~23) 배열

    @NotNull
    private String status; // "POSSIBLE" 또는 "IMPOSSIBLE" (연산 방향 결정용)
}
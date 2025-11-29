package org.dallyeo.matuabom.domain.meeting;

import lombok.Value;
import java.time.Instant;

/**
 * AvailableTimeCalculator의 최종 결과로, 모두에게 가능한 시간 슬롯을 나타냅니다.
 * Instant를 사용하여 Timezone에 독립적인 시점(Point in Time)을 저장합니다.
 */
@Value // ✨ 불변(Immutable) 객체로 정의
public class AvailableSlot {
    private Instant start;
    private Instant end;
}
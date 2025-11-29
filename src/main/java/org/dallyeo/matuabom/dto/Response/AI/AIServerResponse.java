package org.dallyeo.matuabom.dto.Response.AI;

import lombok.Builder;
import lombok.Getter;
import org.dallyeo.matuabom.dto.Response.InputCategory;

@Getter
@Builder
//프론트로 반환하는 DTO
public class AIServerResponse {
    // AI가 분류한 의도 (일정생성, 모임조회 등)
    private InputCategory category;

    private String summary;

    // 각 카테고리에 맞는 상세 데이터 (JSON 객체)
    private Object data;
}

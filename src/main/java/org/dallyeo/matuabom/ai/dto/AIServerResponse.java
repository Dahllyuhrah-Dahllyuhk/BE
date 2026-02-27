package org.dallyeo.matuabom.ai.dto;

import lombok.Builder;
import lombok.Getter;
import org.dallyeo.matuabom.ai.dto.InputCategory;

@Getter
@Builder
public class AIServerResponse {
    private InputCategory category;

    private String summary;

    private Object data;
}

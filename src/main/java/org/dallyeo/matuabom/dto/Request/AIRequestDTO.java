package org.dallyeo.matuabom.dto.Request;

import jakarta.validation.constraints.NotNull;

public record AIRequestDTO(
    @NotNull String userInput //사용자 질의
) {
}

package org.dallyeo.matuabom.ai.dto;

import jakarta.validation.constraints.NotNull;

public record AIRequestDTO(
    @NotNull String prompt
) {
}
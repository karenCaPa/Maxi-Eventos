package com.maxieventos.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdicionalRequest(@NotBlank String codigo, @Positive int cantidad) {
}

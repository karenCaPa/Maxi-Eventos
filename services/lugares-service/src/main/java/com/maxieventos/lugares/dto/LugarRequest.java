package com.maxieventos.lugares.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LugarRequest(@NotBlank String nombre,
                          @NotBlank String direccion,
                          @NotBlank String tipo,
                          @NotNull @Positive Integer capacidad) {
}

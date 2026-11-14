package com.maxieventos.lugares.dto;

import com.maxieventos.lugares.entity.EstadoLugar;
import jakarta.validation.constraints.NotNull;

public record EstadoLugarRequest(@NotNull EstadoLugar estado) {
}

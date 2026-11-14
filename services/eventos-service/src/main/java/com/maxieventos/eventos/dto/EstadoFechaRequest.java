package com.maxieventos.eventos.dto;

import com.maxieventos.eventos.entity.EstadoFecha;
import jakarta.validation.constraints.NotNull;

public record EstadoFechaRequest(@NotNull EstadoFecha estado) {
}

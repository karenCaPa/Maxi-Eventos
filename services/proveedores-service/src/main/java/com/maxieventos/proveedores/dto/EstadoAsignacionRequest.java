package com.maxieventos.proveedores.dto;

import com.maxieventos.proveedores.entity.EstadoAsignacion;
import jakarta.validation.constraints.NotNull;

public record EstadoAsignacionRequest(@NotNull EstadoAsignacion estado) {
}

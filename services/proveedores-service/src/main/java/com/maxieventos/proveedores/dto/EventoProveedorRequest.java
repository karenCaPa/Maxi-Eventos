package com.maxieventos.proveedores.dto;

import com.maxieventos.proveedores.entity.EstadoAsignacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code estado} es opcional: si no se envía, la asignación queda PENDIENTE. */
public record EventoProveedorRequest(
        @NotNull UUID eventoId,
        @NotNull UUID proveedorId,
        @NotBlank String servicioAsignado,
        @NotNull @Positive BigDecimal costoAcordado,
        EstadoAsignacion estado
) {
}

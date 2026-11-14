package com.maxieventos.finanzas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record PagoRequest(
        @NotNull UUID eventoId,
        @NotNull UUID clienteId,
        @NotBlank String metodoPago,
        @NotNull @Positive BigDecimal monto,
        @NotBlank String moneda,
        UUID eventIdempotencia
) {
}

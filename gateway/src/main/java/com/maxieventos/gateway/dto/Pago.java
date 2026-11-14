package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pago de finanzas-service. {@code id} = {@code pagoId}, {@code fecha} = {@code creadoEn},
 * {@code claveIdempotencia} = {@code eventIdempotencia}.
 */
public record Pago(@JsonProperty("pagoId") UUID id, String estado,
                   @JsonProperty("eventIdempotencia") String claveIdempotencia, UUID eventoId, UUID clienteId,
                   BigDecimal monto, String moneda, String metodoPago, @JsonProperty("creadoEn") OffsetDateTime fecha) {
}

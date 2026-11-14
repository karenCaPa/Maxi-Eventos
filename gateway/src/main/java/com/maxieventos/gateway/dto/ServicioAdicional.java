package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Servicio adicional de un evento (finanzas-service). {@code fecha} = {@code creadoEn}. */
public record ServicioAdicional(UUID id, UUID eventoId, String codigo, String nombre, int cantidad, BigDecimal valorUnitario,
                                BigDecimal subtotal, @JsonProperty("creadoEn") OffsetDateTime fecha) {
}

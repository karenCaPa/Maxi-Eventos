package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cotización de finanzas-service. Nombres del esquema de la Entrega 2: {@code totalSinAdicionales} = {@code total} del DTO y
 * {@code depositoInicialRequerido} = {@code montoReservaRequerido}. El campo GraphQL {@code total} (con adicionales) se
 * resuelve aparte con el balance.
 */
public record Cotizacion(UUID id, UUID eventoId, int invitados, String zona, BigDecimal valorBase, BigDecimal recargoZona,
                         @JsonProperty("total") BigDecimal totalSinAdicionales,
                         @JsonProperty("montoReservaRequerido") BigDecimal depositoInicialRequerido,
                         String moneda, OffsetDateTime creadoEn, String tipoEvento, Integer cantidadAdultos,
                         Integer cantidadNinos, Integer adultosAdicionales, Integer ninosAdicionales,
                         BigDecimal valorAdultosAdicionales, BigDecimal valorNinosAdicionales, BigDecimal subtotal) {
}

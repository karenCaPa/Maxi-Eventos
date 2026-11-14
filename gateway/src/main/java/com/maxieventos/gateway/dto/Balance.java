package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance de finanzas-service. {@code totalCotizado} = {@code total} (cotización + adicionales), {@code montoAbonado} =
 * {@code totalAbonado}, {@code valorCotizacion} = {@code valorBase} (total de la cotización) y
 * {@code depositoInicialRequerido} = {@code montoReservaRequerido}.
 */
public record Balance(UUID eventoId, @JsonProperty("valorBase") BigDecimal valorCotizacion, BigDecimal adicionales,
                      @JsonProperty("totalAbonado") BigDecimal montoAbonado, BigDecimal saldoPendiente,
                      @JsonProperty("total") BigDecimal totalCotizado,
                      @JsonProperty("montoReservaRequerido") BigDecimal depositoInicialRequerido, String moneda) {
}

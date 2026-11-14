package com.maxieventos.eventos.dto.remoto;

import java.math.BigDecimal;
import java.util.UUID;

/** Lo que eventos-service usa del balance de finanzas-service ({@code GET /eventos/{eventoId}/balance}). */
public record BalanceRemoto(UUID eventoId, BigDecimal totalAbonado, BigDecimal montoReservaRequerido, String moneda) {
}

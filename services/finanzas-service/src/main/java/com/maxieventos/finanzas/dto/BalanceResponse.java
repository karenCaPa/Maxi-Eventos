package com.maxieventos.finanzas.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance financiero de un evento: cotización + adicionales, lo abonado, el saldo pendiente y el depósito inicial
 * requerido para confirmar el evento (tomado de la cotización).
 */
public record BalanceResponse(UUID eventoId, BigDecimal valorBase, BigDecimal adicionales,
                              BigDecimal totalAbonado, BigDecimal saldoPendiente, BigDecimal total,
                              BigDecimal montoReservaRequerido, String moneda) {
}

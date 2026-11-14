package com.maxieventos.gateway.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Input {@code PagoInput}. {@code claveIdempotencia} no va en el cuerpo: se envía en la cabecera {@code X-Idempotency-Key}
 * de {@code POST /finanzas/pagos} (misma clave = mismo pago).
 */
public record PagoInput(UUID eventoId, UUID clienteId, BigDecimal monto, String moneda, String metodoPago,
                        String claveIdempotencia) {

    /** Cuerpo REST ({@code PagoRequest}). */
    public record Cuerpo(UUID eventoId, UUID clienteId, String metodoPago, BigDecimal monto, String moneda) {
    }

    public Cuerpo cuerpo() {
        return new Cuerpo(eventoId, clienteId, metodoPago, monto, moneda);
    }
}

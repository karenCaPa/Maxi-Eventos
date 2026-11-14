package com.maxieventos.finanzas.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code datos} del mensaje PAGO_REGISTRADO (tópico {@code pagos.registrados}, clave = eventoId). */
public record DatosPagoRegistrado(
        UUID pagoId,
        UUID eventoId,
        UUID clienteId,
        BigDecimal monto,
        String moneda,
        String metodoPago,
        String claveIdempotencia,
        Instant fechaPago) {

    public static final String TIPO = "PAGO_REGISTRADO";
}

package com.maxieventos.notificaciones.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code datos} de PAGO_REGISTRADO con los campos que usa la notificación (los demás se ignoran). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosPagoRegistrado(
        UUID pagoId,
        UUID eventoId,
        UUID clienteId,
        BigDecimal monto,
        String moneda,
        String metodoPago,
        Instant fechaPago) {

    public static final String TIPO = "PAGO_REGISTRADO";
}

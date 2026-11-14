package com.maxieventos.finanzas.kafka;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code datos} del mensaje SERVICIO_ADICIONAL_AGREGADO (tópico {@code servicios-adicionales.agregados}, clave = eventoId). */
public record DatosServicioAdicionalAgregado(
        UUID adicionalId,
        UUID eventoId,
        String codigo,
        String nombre,
        int cantidad,
        BigDecimal valorUnitario,
        BigDecimal subtotal,
        String moneda) {

    public static final String TIPO = "SERVICIO_ADICIONAL_AGREGADO";
}

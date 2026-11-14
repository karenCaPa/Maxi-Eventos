package com.maxieventos.finanzas.dto;

import com.maxieventos.finanzas.entity.Pago;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un abono. {@code eventIdempotencia} es la clave de idempotencia con la que se registró (nula en pagos antiguos). */
public record PagoResponse(UUID pagoId, String estado, String eventIdempotencia, UUID eventoId, UUID clienteId,
                           BigDecimal monto, String moneda, String metodoPago, Instant creadoEn) {

    public static PagoResponse from(Pago pago) {
        return new PagoResponse(pago.getId(), pago.getEstado(), pago.getClaveIdempotencia(), pago.getEventoId(),
                pago.getClienteId(), pago.getMonto(), pago.getMoneda(), pago.getMetodoPago(), pago.getCreadoEn());
    }
}

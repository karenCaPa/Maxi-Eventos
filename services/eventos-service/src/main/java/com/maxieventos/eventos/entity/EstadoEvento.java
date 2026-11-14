package com.maxieventos.eventos.entity;

/**
 * PENDIENTE: creado, aún sin contrato firmado ni reserva pagada. RESERVADO: confirmado (contrato firmado y reserva
 * abonada). CANCELADO: la fecha vuelve a quedar disponible.
 */
public enum EstadoEvento {
    PENDIENTE,
    RESERVADO,
    CANCELADO
}

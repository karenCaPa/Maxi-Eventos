package com.maxieventos.eventos.entity;

/**
 * DISPONIBLE: se puede crear un evento. PENDIENTE: tiene un evento sin confirmar (o un bloqueo manual).
 * OCUPADA: tiene un evento confirmado (o un bloqueo manual).
 */
public enum EstadoFecha {
    DISPONIBLE,
    PENDIENTE,
    OCUPADA
}

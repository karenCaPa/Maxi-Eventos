package com.maxieventos.finanzas.entity;

/**
 * Estado de un mensaje del outbox. PENDIENTE → ENVIANDO (lo tomó el envío inmediato o la tarea de reenvío) →
 * PUBLICADO (Kafka confirmó) o de vuelta a PENDIENTE (falló; se reintentará).
 */
public enum EstadoMensajeSaliente {
    PENDIENTE,
    ENVIANDO,
    PUBLICADO
}

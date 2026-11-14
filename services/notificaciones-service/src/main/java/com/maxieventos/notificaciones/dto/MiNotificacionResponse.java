package com.maxieventos.notificaciones.dto;

import com.maxieventos.notificaciones.entity.EstadoNotificacion;
import com.maxieventos.notificaciones.entity.Notificacion;
import java.time.Instant;
import java.util.UUID;

/**
 * Fase 11H: una notificación vista por el usuario actual (campanita): los datos de la notificación más si ESE usuario ya la
 * leyó y cuándo. {@code fechaCreacion} es cuándo se recibió (cuando el consumidor de Kafka la generó).
 */
public record MiNotificacionResponse(
        UUID id,
        UUID idEvento,
        UUID eventoId,
        String tipo,
        String destinatario,
        String asunto,
        String mensaje,
        EstadoNotificacion estado,
        Instant fechaCreacion,
        boolean leida,
        Instant leidaEn) {

    public static MiNotificacionResponse de(Notificacion n, Instant leidaEn) {
        return new MiNotificacionResponse(n.getId(), n.getIdEvento(), n.getEventoId(), n.getTipo(), n.getDestinatario(),
                n.getAsunto(), n.getMensaje(), n.getEstado(), n.getFechaCreacion(), leidaEn != null, leidaEn);
    }
}

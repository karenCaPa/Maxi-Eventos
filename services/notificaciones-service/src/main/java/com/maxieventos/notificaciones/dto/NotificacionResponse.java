package com.maxieventos.notificaciones.dto;

import com.maxieventos.notificaciones.entity.EstadoNotificacion;
import com.maxieventos.notificaciones.entity.Notificacion;
import java.time.Instant;
import java.util.UUID;

public record NotificacionResponse(
        UUID id,
        UUID idEvento,
        UUID eventoId,
        String tipo,
        String destinatario,
        String asunto,
        String mensaje,
        EstadoNotificacion estado,
        Instant fechaCreacion) {

    public static NotificacionResponse de(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getIdEvento(), n.getEventoId(), n.getTipo(), n.getDestinatario(),
                n.getAsunto(), n.getMensaje(), n.getEstado(), n.getFechaCreacion());
    }
}

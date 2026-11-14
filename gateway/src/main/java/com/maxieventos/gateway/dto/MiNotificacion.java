package com.maxieventos.gateway.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fase 11H: una notificación vista por el usuario actual (campanita), tal como la devuelve notificaciones-service:
 * {@code leida} / {@code leidaEn} son de ESE usuario. {@code evento} se resuelve aparte (BatchMapping, solo si se pide).
 */
public record MiNotificacion(UUID id, UUID idEvento, UUID eventoId, String tipo, String destinatario, String asunto,
                             String mensaje, String estado, OffsetDateTime fechaCreacion, boolean leida,
                             OffsetDateTime leidaEn) {
}

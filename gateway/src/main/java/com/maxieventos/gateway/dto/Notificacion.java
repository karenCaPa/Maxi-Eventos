package com.maxieventos.gateway.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Notificacion(UUID id, UUID idEvento, UUID eventoId, String tipo, String destinatario, String asunto,
                           String mensaje, String estado, OffsetDateTime fechaCreacion) {
}

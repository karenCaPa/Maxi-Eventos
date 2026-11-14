package com.maxieventos.auditoria.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.maxieventos.auditoria.entity.AuditoriaEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Registro de auditoría. {@code payload} se devuelve como el objeto JSON original del mensaje (no como texto escapado). */
public record AuditoriaEventoResponse(
        UUID idEvento,
        String tipo,
        UUID eventoId,
        String servicioOrigen,
        // Springdoc vería un String; en el JSON real es el objeto del sobre (se documenta como un objeto JSON)
        @Schema(implementation = Map.class, description = "Sobre del mensaje Kafka tal como llegó del tópico")
        @JsonRawValue String payload,
        String topico,
        int particion,
        long offset,
        Instant fechaOcurrencia,
        Instant fechaRecepcion) {

    public static AuditoriaEventoResponse de(AuditoriaEvento e) {
        return new AuditoriaEventoResponse(e.getIdEvento(), e.getTipo(), e.getEventoId(), e.getServicioOrigen(),
                e.getPayload(), e.getTopico(), e.getParticion(), e.getOffsetKafka(), e.getFechaOcurrencia(),
                e.getFechaRecepcion());
    }
}

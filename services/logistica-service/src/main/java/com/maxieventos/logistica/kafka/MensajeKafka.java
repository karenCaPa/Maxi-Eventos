package com.maxieventos.logistica.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Sobre común de los mensajes Kafka de Maxi Eventos, tal como lo lee un consumidor: {@code datos} queda como JSON y
 * se convierte al registro de cada tipo de mensaje (así el consumidor no depende de las clases del productor).
 */
public record MensajeKafka(
        UUID idEvento,
        String tipo,
        int version,
        Instant fechaOcurrencia,
        String servicioOrigen,
        UUID eventoId,
        JsonNode datos) {
}

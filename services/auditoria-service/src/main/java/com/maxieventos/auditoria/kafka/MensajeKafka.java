package com.maxieventos.auditoria.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Sobre común de los mensajes Kafka de Maxi Eventos, tal como lo lee un consumidor: {@code datos} queda como JSON.
 * Auditoría conserva además el {@code textoOriginal}: el JSON exacto que llegó del tópico (lo rellena
 * {@link SobreDeserializer}); no forma parte del sobre, por eso no se serializa (p. ej. al enviarlo al DLT).
 */
public record MensajeKafka(
        UUID idEvento,
        String tipo,
        int version,
        Instant fechaOcurrencia,
        String servicioOrigen,
        UUID eventoId,
        JsonNode datos,
        @JsonIgnore String textoOriginal) {

    @JsonCreator
    public MensajeKafka(@JsonProperty("idEvento") UUID idEvento, @JsonProperty("tipo") String tipo,
                        @JsonProperty("version") int version, @JsonProperty("fechaOcurrencia") Instant fechaOcurrencia,
                        @JsonProperty("servicioOrigen") String servicioOrigen, @JsonProperty("eventoId") UUID eventoId,
                        @JsonProperty("datos") JsonNode datos) {
        this(idEvento, tipo, version, fechaOcurrencia, servicioOrigen, eventoId, datos, null);
    }

    public MensajeKafka conTextoOriginal(String texto) {
        return new MensajeKafka(idEvento, tipo, version, fechaOcurrencia, servicioOrigen, eventoId, datos, texto);
    }
}

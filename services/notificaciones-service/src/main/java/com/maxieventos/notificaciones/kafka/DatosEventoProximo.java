package com.maxieventos.notificaciones.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code datos} de EVENTO_PROXIMO (Fase 11E, tópico {@code eventos.proximos}) con los campos que usa la notificación (los
 * demás se ignoran). Copia local: el consumidor no depende de las clases de eventos-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosEventoProximo(
        UUID eventoId,
        String nombreEvento,
        String tipoEvento,
        LocalDate fecha,
        UUID clienteId,
        Integer diasRestantes,
        Integer umbralDias) {

    public static final String TIPO = "EVENTO_PROXIMO";
}

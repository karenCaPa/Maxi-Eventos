package com.maxieventos.notificaciones.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code datos} de EVENTO_CONFIRMADO con los campos que usa la notificación (los demás se ignoran). Copia local: el
 * consumidor no depende de las clases de eventos-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosEventoConfirmado(
        UUID eventoId,
        String nombreEvento,
        String tipoEvento,
        LocalDate fecha,
        UUID clienteId,
        Integer cantidadAdultos,
        Integer cantidadNinos,
        Integer numInvitados) {

    public static final String TIPO = "EVENTO_CONFIRMADO";
}

package com.maxieventos.logistica.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.UUID;

/** Lo que Logística usa de {@code datos} de EVENTO_CONFIRMADO (los campos que no conoce se ignoran). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosEventoConfirmado(
        UUID eventoId,
        String nombreEvento,
        String tipoEvento,
        LocalDate fecha,
        Integer cantidadAdultos,
        Integer cantidadNinos,
        Integer numInvitados,
        String zona) {

    public static final String TIPO = "EVENTO_CONFIRMADO";
}

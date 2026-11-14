package com.maxieventos.eventos.kafka;

import java.time.LocalDate;
import java.util.UUID;

/** {@code datos} del mensaje EVENTO_CONFIRMADO (tópico {@code eventos.confirmados}, clave = eventoId). */
public record DatosEventoConfirmado(
        UUID eventoId,
        String nombreEvento,
        String tipoEvento,
        LocalDate fecha,
        UUID clienteId,
        UUID lugarId,
        int cantidadAdultos,
        int cantidadNinos,
        int numInvitados,
        String zona) {

    public static final String TIPO = "EVENTO_CONFIRMADO";
}

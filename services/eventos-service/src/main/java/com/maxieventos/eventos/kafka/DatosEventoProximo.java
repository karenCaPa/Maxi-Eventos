package com.maxieventos.eventos.kafka;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code datos} del mensaje EVENTO_PROXIMO (Fase 11E; tópico {@code eventos.proximos}, clave = eventoId): un evento
 * RESERVADO cuya fecha está a {@code diasRestantes} días. {@code umbralDias} es el umbral de la configuración que se cumplió
 * (7 o 1 por defecto): hay a lo sumo un mensaje por evento y umbral.
 */
public record DatosEventoProximo(
        UUID eventoId,
        String nombreEvento,
        String tipoEvento,
        LocalDate fecha,
        UUID clienteId,
        UUID lugarId,
        int numInvitados,
        int diasRestantes,
        int umbralDias) {

    public static final String TIPO = "EVENTO_PROXIMO";
}

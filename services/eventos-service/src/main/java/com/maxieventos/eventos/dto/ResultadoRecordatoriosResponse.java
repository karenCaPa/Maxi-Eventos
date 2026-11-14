package com.maxieventos.eventos.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de una revisión de recordatorios (Fase 11E): la programada o la manual ({@code POST /recordatorios/ejecutar}).
 * {@code revisados} = eventos RESERVADOS entre mañana y el umbral mayor; {@code enviados} = recordatorios nuevos (cada uno
 * un EVENTO_PROXIMO en el outbox); {@code yaEnviados} = los que ya se habían enviado antes para ese umbral.
 */
public record ResultadoRecordatoriosResponse(
        Instant ejecutadoEn,
        LocalDate hoy,
        List<Integer> umbralesDias,
        int revisados,
        int enviados,
        int yaEnviados,
        List<Recordatorio> recordatorios) {

    /** {@code resultado}: ENVIADO (nuevo) o YA_ENVIADO (no se repite). */
    public record Recordatorio(UUID eventoId, String nombreEvento, LocalDate fecha, int diasRestantes, int umbralDias,
                               String resultado) {
    }
}

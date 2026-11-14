package com.maxieventos.gateway.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Fase 11E: resultado de {@code POST /api/v1/recordatorios/ejecutar} (eventos-service), tal como lo devuelve el servicio. */
public record ResultadoRecordatorios(OffsetDateTime ejecutadoEn, LocalDate hoy, List<Integer> umbralesDias, int revisados,
                                     int enviados, int yaEnviados, List<Recordatorio> recordatorios) {

    public record Recordatorio(UUID eventoId, String nombreEvento, LocalDate fecha, int diasRestantes, int umbralDias,
                               String resultado) {
    }
}

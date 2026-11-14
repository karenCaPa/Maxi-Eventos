package com.maxieventos.contratos.dto.remoto;

import java.time.LocalDate;
import java.util.UUID;

/** Lo que contratos-service usa de {@code GET /api/v1/eventos/{id}} de eventos-service. */
public record EventoRemoto(UUID id, String nombreEvento, LocalDate fecha, UUID clienteId, String tipoEvento,
                           int cantidadAdultos, int cantidadNinos, String zona, UUID lugarId, String estado) {
}

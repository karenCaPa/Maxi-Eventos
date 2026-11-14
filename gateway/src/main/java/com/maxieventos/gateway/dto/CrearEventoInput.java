package com.maxieventos.gateway.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Input {@code CrearEventoInput}; se envía tal cual a {@code POST /eventos} (mismos nombres que {@code CrearEventoRequest}). */
public record CrearEventoInput(String nombreEvento, UUID clienteId, LocalDate fecha, String tipoEvento, Integer cantidadAdultos,
                               Integer cantidadNinos, String zona, UUID lugarId) {
}

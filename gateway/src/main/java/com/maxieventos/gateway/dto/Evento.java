package com.maxieventos.gateway.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Evento tal como lo devuelve eventos-service ({@code EventoResponse}). Las relaciones se resuelven en los controladores. */
public record Evento(UUID id, String nombreEvento, LocalDate fecha, UUID clienteId, String tipoEvento, int cantidadAdultos,
                     int cantidadNinos, int numInvitados, String zona, UUID lugarId, String estado,
                     OffsetDateTime fechaCreacion) {
}

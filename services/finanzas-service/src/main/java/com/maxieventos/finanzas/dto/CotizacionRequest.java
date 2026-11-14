package com.maxieventos.finanzas.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Solo el evento: tipo, zona, adultos y niños se consultan a eventos-service. Los campos antiguos
 * ({@code invitados}, {@code zona}) se aceptan por compatibilidad y se ignoran.
 */
public record CotizacionRequest(@NotNull UUID eventoId) {
}

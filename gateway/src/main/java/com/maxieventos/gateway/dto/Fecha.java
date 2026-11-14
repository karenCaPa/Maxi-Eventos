package com.maxieventos.gateway.dto;

import java.time.LocalDate;
import java.util.UUID;

public record Fecha(LocalDate fecha, String diaSemana, String estado, UUID eventoId) {
}

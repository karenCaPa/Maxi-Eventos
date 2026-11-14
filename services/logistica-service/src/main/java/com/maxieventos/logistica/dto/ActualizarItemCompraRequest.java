package com.maxieventos.logistica.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActualizarItemCompraRequest(
        @NotNull UUID id,
        @NotNull Boolean comprado) {
}
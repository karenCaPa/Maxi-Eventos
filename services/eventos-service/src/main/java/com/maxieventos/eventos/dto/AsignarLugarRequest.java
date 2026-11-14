package com.maxieventos.eventos.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AsignarLugarRequest(@NotNull UUID lugarId) {
}

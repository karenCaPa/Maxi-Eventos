package com.maxieventos.logistica.dto;

import java.util.UUID;

public record RequerimientosLogistica(
        UUID eventoId,
        Integer meserosRequeridos,
        Integer personalCocinaRequerido,
        Integer camionesTransporte) {
}
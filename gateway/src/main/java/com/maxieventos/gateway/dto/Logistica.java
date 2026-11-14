package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Requerimientos de logistica-service. Nombres del esquema de la Entrega 2: {@code meseros} = {@code meserosRequeridos},
 * {@code personalCocina} = {@code personalCocinaRequerido} y {@code vehiculos} = {@code camionesTransporte}.
 */
public record Logistica(UUID eventoId, @JsonProperty("meserosRequeridos") Integer meseros,
                        @JsonProperty("personalCocinaRequerido") Integer personalCocina,
                        @JsonProperty("camionesTransporte") Integer vehiculos) {
}

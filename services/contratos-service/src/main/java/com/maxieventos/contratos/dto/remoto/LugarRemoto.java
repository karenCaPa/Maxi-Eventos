package com.maxieventos.contratos.dto.remoto;

import java.util.UUID;

/** Lo que contratos-service usa de {@code GET /api/v1/lugares/{id}} de lugares-service. */
public record LugarRemoto(UUID id, String nombre, String direccion) {
}

package com.maxieventos.contratos.dto.remoto;

import java.util.UUID;

/** Lo que contratos-service usa de {@code GET /api/v1/clientes/{id}} de clientes-service. */
public record ClienteRemoto(UUID id, String nombreCompleto, String telefono, String correo) {
}

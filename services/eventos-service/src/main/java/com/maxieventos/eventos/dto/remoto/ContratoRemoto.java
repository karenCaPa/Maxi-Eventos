package com.maxieventos.eventos.dto.remoto;

import java.util.UUID;

/** Lo que eventos-service usa de la respuesta de contratos-service ({@code GET /eventos/{eventoId}/contrato}). */
public record ContratoRemoto(UUID id, UUID eventoId, String estado) {

    public static final String FIRMADO = "FIRMADO";

    public boolean firmado() {
        return FIRMADO.equals(estado);
    }
}

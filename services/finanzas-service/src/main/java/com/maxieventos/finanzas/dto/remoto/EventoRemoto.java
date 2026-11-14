package com.maxieventos.finanzas.dto.remoto;

import com.maxieventos.finanzas.entity.TipoEvento;
import com.maxieventos.finanzas.entity.ZonaEvento;
import java.util.UUID;

/** Lo que finanzas-service usa de la respuesta de {@code GET /api/v1/eventos/{id}} de eventos-service. */
public record EventoRemoto(UUID id, String nombreEvento, UUID clienteId, TipoEvento tipoEvento,
                           int cantidadAdultos, int cantidadNinos, ZonaEvento zona, String estado) {

    public boolean esPendiente() {
        return "PENDIENTE".equals(estado);
    }

    public boolean esCancelado() {
        return "CANCELADO".equals(estado);
    }
}

package com.maxieventos.proveedores.dto;

import com.maxieventos.proveedores.entity.EstadoAsignacion;
import com.maxieventos.proveedores.entity.EventoProveedor;
import java.math.BigDecimal;
import java.util.UUID;

public record EventoProveedorResponseDTO(UUID id, UUID eventoId, UUID proveedorId, String servicioAsignado,
                                         BigDecimal costoAcordado, EstadoAsignacion estado) {
    public static EventoProveedorResponseDTO from(EventoProveedor asignacion) {
        return new EventoProveedorResponseDTO(
                asignacion.getId(),
                asignacion.getEventoId(),
                asignacion.getProveedorId(),
                asignacion.getServicioAsignado(),
                asignacion.getCostoAcordado(),
                asignacion.getEstado()
        );
    }
}

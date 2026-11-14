package com.maxieventos.gateway.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Input {@code AsignarProveedorInput}. {@code servicio} se envía como {@code servicioAsignado} ({@code EventoProveedorRequest}). */
public record AsignarProveedorInput(UUID eventoId, UUID proveedorId, String servicio, BigDecimal costoAcordado, String estado) {

    /** Cuerpo REST ({@code EventoProveedorRequest}). */
    public record Cuerpo(UUID eventoId, UUID proveedorId, String servicioAsignado, BigDecimal costoAcordado, String estado) {
    }

    public Cuerpo cuerpo() {
        return new Cuerpo(eventoId, proveedorId, servicio, costoAcordado, estado);
    }
}

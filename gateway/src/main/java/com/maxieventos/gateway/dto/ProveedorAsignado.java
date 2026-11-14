package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Asignación de proveedores-service. {@code servicio} = {@code servicioAsignado}; la categoría sale del proveedor. */
public record ProveedorAsignado(UUID id, UUID eventoId, UUID proveedorId,
                                @JsonProperty("servicioAsignado") String servicio, BigDecimal costoAcordado,
                                String estado) {
}

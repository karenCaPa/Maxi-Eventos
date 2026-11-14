package com.maxieventos.contratos.dto.remoto;

import java.math.BigDecimal;
import java.util.UUID;

/** Lo que contratos-service usa de {@code GET /api/v1/eventos/{id}/cotizacion} de finanzas-service. */
public record CotizacionRemota(UUID id, UUID eventoId, BigDecimal total, String moneda) {
}

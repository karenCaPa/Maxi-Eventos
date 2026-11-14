package com.maxieventos.finanzas.dto;

import com.maxieventos.finanzas.entity.AdicionalEvento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdicionalEventoResponseDTO(UUID id, UUID eventoId, String codigo, String nombre,
                                        int cantidad, BigDecimal valorUnitario,
                                        BigDecimal subtotal, Instant creadoEn) {
    public static AdicionalEventoResponseDTO from(AdicionalEvento adicional) {
        return new AdicionalEventoResponseDTO(
                adicional.getId(),
                adicional.getEventoId(),
                adicional.getCodigo(),
                adicional.getNombre(),
                adicional.getCantidad(),
                adicional.getValorUnitario(),
                adicional.getSubtotal(),
                adicional.getCreadoEn()
        );
    }
}

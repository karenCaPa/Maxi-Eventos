package com.maxieventos.finanzas.dto;

import com.maxieventos.finanzas.entity.Cotizacion;
import com.maxieventos.finanzas.entity.TipoEvento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cotización con su desglose. Los campos del desglose (tipoEvento, cantidadAdultos, cantidadNinos, adultosAdicionales,
 * ninosAdicionales, valorAdultosAdicionales, valorNinosAdicionales, subtotal) son nulos en cotizaciones anteriores a la Fase 4.
 */
public record CotizacionResponseDTO(UUID id, UUID eventoId, int invitados, String zona,
                                   BigDecimal valorBase, BigDecimal recargoZona,
                                   BigDecimal total, BigDecimal montoReservaRequerido,
                                   String moneda, Instant creadoEn,
                                   TipoEvento tipoEvento, Integer cantidadAdultos, Integer cantidadNinos,
                                   Integer adultosAdicionales, Integer ninosAdicionales,
                                   BigDecimal valorAdultosAdicionales, BigDecimal valorNinosAdicionales,
                                   BigDecimal subtotal) {

    /** {@code montoReservaRequerido} lo resuelve el service (cotizaciones antiguas sin el valor guardado). */
    public static CotizacionResponseDTO from(Cotizacion cotizacion, BigDecimal montoReservaRequerido) {
        return new CotizacionResponseDTO(
                cotizacion.getId(),
                cotizacion.getEventoId(),
                cotizacion.getInvitados(),
                cotizacion.getZona(),
                cotizacion.getValorBase(),
                cotizacion.getRecargoZona(),
                cotizacion.getTotal(),
                montoReservaRequerido,
                cotizacion.getMoneda(),
                cotizacion.getCreadoEn(),
                cotizacion.getTipoEvento(),
                cotizacion.getCantidadAdultos(),
                cotizacion.getCantidadNinos(),
                cotizacion.getAdultosAdicionales(),
                cotizacion.getNinosAdicionales(),
                cotizacion.getValorAdultosAdicionales(),
                cotizacion.getValorNinosAdicionales(),
                cotizacion.getSubtotal()
        );
    }
}

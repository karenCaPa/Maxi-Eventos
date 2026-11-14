package com.maxieventos.finanzas.entity;

import java.math.BigDecimal;

/** Resultado del cálculo de la cotización, con cada componente del precio. Importes con 2 decimales. */
public record DesgloseCotizacion(
        TipoEvento tipoEvento,
        ZonaEvento zona,
        int cantidadAdultos,
        int cantidadNinos,
        BigDecimal valorBase,
        int adultosAdicionales,
        int ninosAdicionales,
        BigDecimal valorAdultosAdicionales,
        BigDecimal valorNinosAdicionales,
        BigDecimal subtotal,
        BigDecimal recargoZona,
        BigDecimal total,
        BigDecimal montoReservaRequerido) {
}

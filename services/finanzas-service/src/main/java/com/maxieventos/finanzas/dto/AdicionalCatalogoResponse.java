package com.maxieventos.finanzas.dto;

import java.math.BigDecimal;

/**
 * Servicio adicional del catálogo (valores tomados de la configuración).
 */
public record AdicionalCatalogoResponse(String codigo, String nombre, BigDecimal costoUnitario, String moneda) {
}

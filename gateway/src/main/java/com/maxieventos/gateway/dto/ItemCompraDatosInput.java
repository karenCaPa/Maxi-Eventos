package com.maxieventos.gateway.dto;

/**
 * Input {@code ItemCompraDatosInput} (10B3); se envía tal cual a {@code POST /eventos/{id}/compras} y
 * {@code PUT /eventos/{id}/compras/{itemId}} (mismos nombres que {@code ItemCompraRequest} de logística).
 */
public record ItemCompraDatosInput(String nombreItem, String cantidadEstimada, String nota) {
}

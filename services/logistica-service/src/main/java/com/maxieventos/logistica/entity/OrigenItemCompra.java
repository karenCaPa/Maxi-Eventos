package com.maxieventos.logistica.entity;

/**
 * De dónde sale un ítem de la lista de compras (10B3): CALCULADO lo genera el sistema al confirmar el evento (hielo,
 * servilletas, bebidas…); MANUAL lo agrega la organizadora. Solo los MANUAL se pueden quitar.
 */
public enum OrigenItemCompra {
    CALCULADO,
    MANUAL
}

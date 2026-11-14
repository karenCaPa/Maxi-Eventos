package com.maxieventos.gateway.exception;

/** Detalle por campo de un 400 de los servicios ({@code errores[]} del {@code ErrorResponse} común). */
public record ErrorCampo(String campo, String mensaje) {
}

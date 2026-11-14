package com.maxieventos.auth.dto;

/**
 * Respuesta del login, alineada con el contrato {@code AuthResponse} de {@code openapi/auth.yaml}. Desde la Fase 11D
 * incluye el {@code nombre} (para la barra de la interfaz; NO va en el token) y el {@code rol} (también va en el claim
 * {@code rol} del token).
 */
public record LoginResponse(String token, String tipoToken, long expiracionEnSegundos, String nombre, String rol) {
}

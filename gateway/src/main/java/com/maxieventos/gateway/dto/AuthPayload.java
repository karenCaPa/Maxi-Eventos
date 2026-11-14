package com.maxieventos.gateway.dto;

/** Respuesta de {@code login}. Fase 11D: {@code nombre} (no va en el token) y {@code rol} (también en el claim del token). */
public record AuthPayload(String token, String tipoToken, long expiracionEnSegundos, String nombre, String rol) {
}

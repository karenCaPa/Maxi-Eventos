package com.maxieventos.finanzas.dto;

/**
 * Resultado de una operación de creación idempotente: el recurso y si se creó ahora ({@code true} → 201)
 * o ya existía ({@code false} → 200).
 */
public record ResultadoCreacion<T>(T recurso, boolean creado) {
}

package com.maxieventos.eventos.dto.remoto;

import java.util.UUID;

/**
 * Lo que eventos-service usa de la respuesta de lugares-service. {@code estado} y {@code capacidad} pueden no venir:
 * en ese caso no se valida esa regla (estado se agrega a Lugares en la Fase 4).
 */
public record LugarRemoto(UUID id, String nombre, Integer capacidad, String estado) {
}

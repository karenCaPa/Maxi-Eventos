package com.maxieventos.logistica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agregar o editar un ítem de la lista de compras (10B3). {@code cantidadEstimada} es texto: una cantidad o una
 * descripción ("20 bolsas", "una por mesa"). La marca de comprado no se cambia aquí (PUT /compras).
 */
public record ItemCompraRequest(
        @NotBlank @Size(max = 120) String nombreItem,
        @NotBlank @Size(max = 80) String cantidadEstimada,
        @Size(max = 255) String nota) {
}

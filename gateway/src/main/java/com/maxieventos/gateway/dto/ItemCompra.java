package com.maxieventos.gateway.dto;

import java.util.UUID;

/** Ítem de la lista de compras de logística. {@code origen} (CALCULADO | MANUAL) y {@code nota} desde la 10B3. */
public record ItemCompra(UUID id, String nombreItem, String cantidadEstimada, boolean comprado, String origen, String nota) {
}

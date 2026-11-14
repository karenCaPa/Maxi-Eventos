package com.maxieventos.gateway.dto;

import java.util.UUID;

/** Ítem de compra y su nuevo estado; se envía tal cual a {@code PUT /eventos/{id}/compras} de Logística. */
public record ItemCompraInput(UUID id, boolean comprado) {
}

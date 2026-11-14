package com.maxieventos.logistica.dto;

import java.util.UUID;

public record ItemCompra(UUID id, String nombreItem, String cantidadEstimada, boolean comprado) {
}
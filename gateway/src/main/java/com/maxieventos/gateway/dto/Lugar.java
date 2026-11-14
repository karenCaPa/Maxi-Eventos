package com.maxieventos.gateway.dto;

import java.util.UUID;

public record Lugar(UUID id, String nombre, String direccion, String tipo, Integer capacidad, String estado) {
}

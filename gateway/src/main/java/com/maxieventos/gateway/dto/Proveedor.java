package com.maxieventos.gateway.dto;

import java.util.UUID;

public record Proveedor(UUID id, String nombre, String categoria, String contacto, boolean activo) {
}

package com.maxieventos.gateway.dto;

import java.util.UUID;

public record Cliente(UUID id, String nombreCompleto, String telefono, String correo, String direccion) {
}

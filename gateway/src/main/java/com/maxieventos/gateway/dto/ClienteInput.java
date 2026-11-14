package com.maxieventos.gateway.dto;

/** Input {@code ClienteInput}; se envía tal cual a {@code POST /clientes} (mismos nombres que {@code ClienteRequest}). */
public record ClienteInput(String nombreCompleto, String telefono, String correo, String direccion) {
}

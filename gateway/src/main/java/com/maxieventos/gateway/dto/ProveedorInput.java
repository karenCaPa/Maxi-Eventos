package com.maxieventos.gateway.dto;

/** Input {@code ProveedorInput}; se envía tal cual a {@code POST /proveedores} y {@code PUT /proveedores/{id}} (mismos nombres que {@code ProveedorRequest}). */
public record ProveedorInput(String nombre, String categoria, String contacto) {
}

package com.maxieventos.proveedores.dto;

import jakarta.validation.constraints.NotBlank;

public record ProveedorRequest(
        @NotBlank String nombre,
        @NotBlank String categoria,
        @NotBlank String contacto
) {
}

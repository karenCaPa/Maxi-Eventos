package com.maxieventos.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank @Size(min = 3, max = 100) String nombreCompleto,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "debe tener entre 7 y 15 dígitos y solo puede empezar con +") @Size(min = 7, max = 16) String telefono,
        @NotBlank @Email @Size(max = 150) String correo,
        @Size(max = 250) String direccion) {
}

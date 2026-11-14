package com.maxieventos.clientes.dto;

import com.maxieventos.clientes.entity.Cliente;

import java.util.UUID;

public record ClienteResponse(
        UUID id,
        String nombreCompleto,
        String telefono,
        String correo,
        String direccion) {

    public static ClienteResponse from(Cliente cliente) {
        return new ClienteResponse(
                cliente.getId(),
                cliente.getNombreCompleto(),
                cliente.getTelefono(),
                cliente.getCorreo(),
                cliente.getDireccion());
    }
}

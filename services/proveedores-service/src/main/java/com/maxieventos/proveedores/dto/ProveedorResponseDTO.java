package com.maxieventos.proveedores.dto;

import com.maxieventos.proveedores.entity.Proveedor;
import java.util.UUID;

public record ProveedorResponseDTO(UUID id, String nombre, String categoria, String contacto, boolean activo) {
    public static ProveedorResponseDTO from(Proveedor proveedor) {
        return new ProveedorResponseDTO(
                proveedor.getId(),
                proveedor.getNombre(),
                proveedor.getCategoria(),
                proveedor.getContacto(),
                proveedor.isActivo()
        );
    }
}

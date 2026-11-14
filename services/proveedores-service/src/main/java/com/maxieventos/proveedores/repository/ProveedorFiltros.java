package com.maxieventos.proveedores.repository;

import com.maxieventos.proveedores.entity.Proveedor;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionales del listado de proveedores; un parámetro nulo o vacío no filtra. */
public final class ProveedorFiltros {

    private ProveedorFiltros() {
    }

    /** Categoría: igual, sin distinguir mayúsculas ni espacios extremos. */
    public static Specification<Proveedor> de(String categoria, Boolean activo) {
        String categoriaBuscada = categoria == null || categoria.isBlank() ? null : categoria.trim().toLowerCase();
        return Specification.<Proveedor>where(null)
                .and(categoriaBuscada == null ? null
                        : (r, q, cb) -> cb.equal(cb.lower(cb.trim(r.get("categoria"))), categoriaBuscada))
                .and(activo == null ? null : (r, q, cb) -> cb.equal(r.get("activo"), activo));
    }
}

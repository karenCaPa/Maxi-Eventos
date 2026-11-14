package com.maxieventos.lugares.repository;

import com.maxieventos.lugares.entity.EstadoLugar;
import com.maxieventos.lugares.entity.Lugar;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros opcionales del listado de lugares; un parámetro nulo o vacío no filtra.
 * Nombre: contiene, sin distinguir mayúsculas. Tipo: igual, sin distinguir mayúsculas.
 */
public final class LugarFiltros {

    private LugarFiltros() {
    }

    public static Specification<Lugar> de(String nombre, String tipo, EstadoLugar estado) {
        String nombreBuscado = normalizar(nombre);
        String tipoBuscado = normalizar(tipo);
        return Specification.<Lugar>where(null)
                .and(nombreBuscado == null ? null
                        : (r, q, cb) -> cb.like(cb.lower(r.get("nombre")), "%" + nombreBuscado + "%"))
                .and(tipoBuscado == null ? null : (r, q, cb) -> cb.equal(cb.lower(r.get("tipo")), tipoBuscado))
                .and(estado == null ? null : (r, q, cb) -> cb.equal(r.get("estado"), estado));
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toLowerCase();
    }
}

package com.maxieventos.notificaciones.controller;

import com.maxieventos.notificaciones.exception.SolicitudInvalidaException;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Construye el {@link Pageable} de los listados a partir de los parámetros {@code page}, {@code size} y {@code sort}.
 * {@code page} y {@code size} se validan en el controller ({@code @Min(0)}, {@code @Min(1) @Max(100)});
 * aquí se valida {@code sort} contra una lista blanca de campos, para responder 400 y no 500.
 */
public final class Paginacion {

    public static final int TAMANO_MAXIMO = 100;

    private Paginacion() {
    }

    public static Pageable crear(int page, int size, String sort, Set<String> camposPermitidos, Sort porDefecto) {
        return PageRequest.of(page, size, ordenar(sort, camposPermitidos, porDefecto));
    }

    private static Sort ordenar(String sort, Set<String> camposPermitidos, Sort porDefecto) {
        if (sort == null || sort.isBlank()) {
            return porDefecto;
        }
        String mensaje = "debe tener el formato campo,asc o campo,desc; campos permitidos: "
                + String.join(", ", new TreeSet<>(camposPermitidos));
        String[] partes = sort.split(",", -1);
        if (partes.length > 2 || !camposPermitidos.contains(partes[0].trim())) {
            throw new SolicitudInvalidaException("sort", mensaje);
        }
        Sort.Direction direccion = partes.length == 1 ? Sort.Direction.ASC
                : Sort.Direction.fromOptionalString(partes[1].trim())
                        .orElseThrow(() -> new SolicitudInvalidaException("sort", mensaje));
        return Sort.by(direccion, partes[0].trim());
    }
}

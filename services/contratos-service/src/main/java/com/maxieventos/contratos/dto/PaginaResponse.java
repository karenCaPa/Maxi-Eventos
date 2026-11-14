package com.maxieventos.contratos.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados de un listado. Formato propio y estable de la API (también se usa en la caché):
 * no expone los detalles internos del {@code Page} de Spring.
 */
public record PaginaResponse<T>(
        List<T> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
        return de(pagina.map(conversor));
    }
}

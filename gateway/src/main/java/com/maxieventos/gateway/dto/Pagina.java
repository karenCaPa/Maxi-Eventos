package com.maxieventos.gateway.dto;

import java.util.List;

/** Página de resultados: el mismo formato que {@code PaginaResponse} de los servicios. */
public record Pagina<T>(List<T> contenido, int pagina, int tamanoPagina, long totalElementos, int totalPaginas) {
}

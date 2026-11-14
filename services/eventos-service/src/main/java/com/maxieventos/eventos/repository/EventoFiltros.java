package com.maxieventos.eventos.repository;

import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.TipoEvento;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** Filtros opcionales del listado de eventos; un parámetro nulo no filtra. */
public final class EventoFiltros {

    private EventoFiltros() {
    }

    public static Specification<Evento> de(EstadoEvento estado, TipoEvento tipo, LocalDate desde, LocalDate hasta) {
        return Specification.<Evento>where(null)
                .and(estado == null ? null : (r, q, cb) -> cb.equal(r.get("estado"), estado))
                .and(tipo == null ? null : (r, q, cb) -> cb.equal(r.get("tipoEvento"), tipo))
                .and(desde == null ? null : (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fecha"), desde))
                .and(hasta == null ? null : (r, q, cb) -> cb.lessThanOrEqualTo(r.get("fecha"), hasta));
    }
}

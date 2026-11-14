package com.maxieventos.eventos.repository;

import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.Evento;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Los filtros del listado (estado, tipo, rango de fechas) se arman con {@code EventoFiltros}. */
public interface EventoRepository extends JpaRepository<Evento, UUID>, JpaSpecificationExecutor<Evento> {

    /** Fase 11E: los candidatos a recordatorio (RESERVADOS entre mañana y el umbral mayor), por fecha. */
    List<Evento> findByEstadoAndFechaBetweenOrderByFechaAsc(EstadoEvento estado, LocalDate desde, LocalDate hasta);
}

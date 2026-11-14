package com.maxieventos.eventos.repository;

import com.maxieventos.eventos.entity.FechaAgenda;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FechaAgendaRepository extends JpaRepository<FechaAgenda, LocalDate> {
    List<FechaAgenda> findAllByFechaBetweenOrderByFechaAsc(LocalDate desde, LocalDate hasta);

    List<FechaAgenda> findAllByFechaIn(Collection<LocalDate> fechas);
}

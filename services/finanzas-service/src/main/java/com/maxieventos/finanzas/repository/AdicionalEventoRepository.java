package com.maxieventos.finanzas.repository;

import com.maxieventos.finanzas.entity.AdicionalEvento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdicionalEventoRepository extends JpaRepository<AdicionalEvento, UUID> {
    List<AdicionalEvento> findAllByEventoIdOrderByCreadoEnAsc(UUID eventoId);

    @Query("select coalesce(sum(a.subtotal), 0) from AdicionalEvento a where a.eventoId = :eventoId")
    BigDecimal totalPorEvento(@Param("eventoId") UUID eventoId);
}

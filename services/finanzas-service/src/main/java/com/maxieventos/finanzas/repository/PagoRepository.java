package com.maxieventos.finanzas.repository;

import com.maxieventos.finanzas.entity.Pago;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, UUID> {
    Page<Pago> findByEventoId(UUID eventoId, Pageable pageable);

    Optional<Pago> findByClaveIdempotencia(String claveIdempotencia);

    @Query("select coalesce(sum(p.monto), 0) from Pago p where p.eventoId = :eventoId")
    BigDecimal totalAbonado(@Param("eventoId") UUID eventoId);
}

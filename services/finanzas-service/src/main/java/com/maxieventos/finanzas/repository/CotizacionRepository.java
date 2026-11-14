package com.maxieventos.finanzas.repository;

import com.maxieventos.finanzas.entity.Cotizacion;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {
    Optional<Cotizacion> findByEventoId(UUID eventoId);
    boolean existsByEventoId(UUID eventoId);

    /**
     * Cotización con bloqueo de fila ({@code SELECT ... FOR UPDATE}): serializa los pagos de un mismo evento para que
     * dos abonos simultáneos no superen juntos el saldo. Solo dentro de una transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cotizacion c where c.eventoId = :eventoId")
    Optional<Cotizacion> bloquearPorEventoId(@Param("eventoId") UUID eventoId);
}

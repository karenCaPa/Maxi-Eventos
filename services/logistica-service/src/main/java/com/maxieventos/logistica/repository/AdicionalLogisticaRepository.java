package com.maxieventos.logistica.repository;

import com.maxieventos.logistica.entity.AdicionalLogistica;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdicionalLogisticaRepository extends JpaRepository<AdicionalLogistica, UUID> {
    List<AdicionalLogistica> findByEventoIdAndAplicadoFalse(UUID eventoId);
}

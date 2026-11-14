package com.maxieventos.contratos.repository;

import com.maxieventos.contratos.entity.ContratoAdicional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoAdicionalRepository extends JpaRepository<ContratoAdicional, UUID> {
    List<ContratoAdicional> findByEventoIdOrderByRecibidoEnAsc(UUID eventoId);
}

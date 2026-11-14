package com.maxieventos.contratos.repository;

import com.maxieventos.contratos.entity.Contrato;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoRepository extends JpaRepository<Contrato, UUID> {
    java.util.Optional<Contrato> findByEventoId(UUID eventoId);
}

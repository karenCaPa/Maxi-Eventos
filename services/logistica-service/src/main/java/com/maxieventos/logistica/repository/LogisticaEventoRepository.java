package com.maxieventos.logistica.repository;

import com.maxieventos.logistica.entity.LogisticaEvento;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LogisticaEventoRepository extends JpaRepository<LogisticaEvento, UUID> {
}
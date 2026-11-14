package com.maxieventos.logistica.repository;

import com.maxieventos.logistica.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {
}

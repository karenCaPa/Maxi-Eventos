package com.maxieventos.contratos.repository;

import com.maxieventos.contratos.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {
}

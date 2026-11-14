package com.maxieventos.auditoria.repository;

import com.maxieventos.auditoria.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {
}

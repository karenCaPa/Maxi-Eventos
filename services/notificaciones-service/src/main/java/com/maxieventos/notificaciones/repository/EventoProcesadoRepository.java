package com.maxieventos.notificaciones.repository;

import com.maxieventos.notificaciones.entity.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {
}

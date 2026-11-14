package com.maxieventos.notificaciones.repository;

import com.maxieventos.notificaciones.entity.Notificacion;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {
    Page<Notificacion> findAllByEventoId(UUID eventoId, Pageable pageable);
}

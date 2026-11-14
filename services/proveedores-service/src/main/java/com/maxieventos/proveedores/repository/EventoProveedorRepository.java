package com.maxieventos.proveedores.repository;

import com.maxieventos.proveedores.entity.EventoProveedor;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProveedorRepository extends JpaRepository<EventoProveedor, UUID> {
    Page<EventoProveedor> findByEventoId(UUID eventoId, Pageable pageable);

    boolean existsByProveedorId(UUID proveedorId);

    boolean existsByEventoIdAndProveedorIdAndServicioAsignado(UUID eventoId, UUID proveedorId, String servicioAsignado);
}

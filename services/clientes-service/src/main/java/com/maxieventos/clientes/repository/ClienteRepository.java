package com.maxieventos.clientes.repository;

import com.maxieventos.clientes.entity.Cliente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Page<Cliente> findByNombreCompletoContainingIgnoreCase(String nombre, Pageable pageable);

    boolean existsByCorreo(String correo);

    boolean existsByTelefono(String telefono);

    boolean existsByCorreoAndIdNot(String correo, UUID id);

    boolean existsByTelefonoAndIdNot(String telefono, UUID id);
}

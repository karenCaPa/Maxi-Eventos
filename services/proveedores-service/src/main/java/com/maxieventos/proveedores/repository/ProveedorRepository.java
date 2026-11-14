package com.maxieventos.proveedores.repository;

import com.maxieventos.proveedores.entity.Proveedor;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProveedorRepository extends JpaRepository<Proveedor, UUID>, JpaSpecificationExecutor<Proveedor> {

    /** Duplicado: mismo nombre sin distinguir mayúsculas ni espacios extremos ({@code nombre} ya llega recortado). */
    @Query("select count(p) > 0 from Proveedor p where lower(trim(p.nombre)) = lower(:nombre)")
    boolean existeConNombre(@Param("nombre") String nombre);

    @Query("select count(p) > 0 from Proveedor p where lower(trim(p.nombre)) = lower(:nombre) and p.id <> :id")
    boolean existeOtroConNombre(@Param("nombre") String nombre, @Param("id") UUID id);
}

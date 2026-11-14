package com.maxieventos.auth.repository;

import com.maxieventos.auth.entity.Rol;
import com.maxieventos.auth.entity.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** {@code correo} ya normalizado (minúsculas, sin espacios en los extremos). */
    Optional<Usuario> findByCorreo(String correo);

    List<Usuario> findByRol(Rol rol);
}

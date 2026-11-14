package com.maxieventos.auth.service;

import com.maxieventos.auth.entity.Rol;
import com.maxieventos.auth.entity.Usuario;
import com.maxieventos.auth.repository.UsuarioRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra (o actualiza) los dos usuarios de la tabla {@code usuarios} desde las variables de entorno en cada arranque
 * (Fase 11D). Así rotar una contraseña sigue siendo cambiar el hash en el {@code .env} y reiniciar auth-service.
 * <ul>
 *   <li>ADMINISTRADOR: {@code AUTH_ORGANIZER_EMAIL}, {@code AUTH_ORGANIZER_ID}, {@code AUTH_ORGANIZER_PASSWORD_HASH} y
 *       {@code AUTH_ORGANIZER_NOMBRE} (las mismas variables de antes de la 11D, más el nombre).</li>
 *   <li>ASISTENTE: {@code AUTH_ASISTENTE_EMAIL}, {@code AUTH_ASISTENTE_ID}, {@code AUTH_ASISTENTE_PASSWORD_HASH} y
 *       {@code AUTH_ASISTENTE_NOMBRE}. Si no se definen, no hay asistente (y uno sembrado antes queda inactivo).</li>
 * </ul>
 * Un usuario de un rol cuyo id ya no está en el {@code .env} queda inactivo (no puede iniciar sesión). Datos inválidos
 * (id que no es UUID, hash que no es BCrypt, el mismo correo para los dos) → auth-service no arranca, con un mensaje claro.
 */
@Component
public class SembradoUsuarios implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SembradoUsuarios.class);

    private final UsuarioRepository repositorio;
    private final DatosUsuario administrador;
    private final DatosUsuario asistente;

    public SembradoUsuarios(UsuarioRepository repositorio,
                            @Value("${auth.usuarios.administrador.id}") String adminId,
                            @Value("${auth.usuarios.administrador.correo}") String adminCorreo,
                            @Value("${auth.usuarios.administrador.nombre}") String adminNombre,
                            @Value("${auth.usuarios.administrador.password-hash}") String adminHash,
                            @Value("${auth.usuarios.asistente.id:}") String asistenteId,
                            @Value("${auth.usuarios.asistente.correo:}") String asistenteCorreo,
                            @Value("${auth.usuarios.asistente.nombre:Asistente}") String asistenteNombre,
                            @Value("${auth.usuarios.asistente.password-hash:}") String asistenteHash) {
        this.repositorio = repositorio;
        this.administrador = DatosUsuario.validar(Rol.ADMINISTRADOR, "AUTH_ORGANIZER", adminId, adminCorreo, adminNombre, adminHash);
        this.asistente = asistenteCorreo == null || asistenteCorreo.isBlank() ? null
                : DatosUsuario.validar(Rol.ASISTENTE, "AUTH_ASISTENTE", asistenteId, asistenteCorreo, asistenteNombre, asistenteHash);
        if (asistente != null && asistente.correo().equals(administrador.correo())) {
            throw new IllegalStateException("AUTH_ASISTENTE_EMAIL no puede ser el mismo correo del administrador.");
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        sembrar(administrador);
        if (asistente != null) {
            sembrar(asistente);
        } else {
            LOGGER.warn("Sin AUTH_ASISTENTE_EMAIL: no hay usuario ASISTENTE.");
        }
        desactivarOtros(Rol.ADMINISTRADOR, administrador.id());
        desactivarOtros(Rol.ASISTENTE, asistente == null ? null : asistente.id());
    }

    private void sembrar(DatosUsuario datos) {
        repositorio.findByCorreo(datos.correo()).filter(u -> !u.getId().equals(datos.id())).ifPresent(u -> {
            throw new IllegalStateException("El correo de " + datos.prefijo() + "_EMAIL ya lo usa otro usuario (id "
                    + u.getId() + "). Cambia el correo o el id en el .env.");
        });
        Usuario usuario = repositorio.findById(datos.id())
                .map(u -> { u.actualizar(datos.correo(), datos.nombre(), datos.rol(), datos.hash()); return u; })
                .orElseGet(() -> new Usuario(datos.id(), datos.correo(), datos.nombre(), datos.rol(), datos.hash()));
        repositorio.save(usuario);
        LOGGER.info("Usuario {} listo. id={}, correo={}", datos.rol(), datos.id(), AuthService.enmascarar(datos.correo()));
    }

    private void desactivarOtros(Rol rol, UUID vigente) {
        repositorio.findByRol(rol).stream().filter(u -> u.isActivo() && !u.getId().equals(vigente)).forEach(u -> {
            u.desactivar();
            repositorio.save(u);
            LOGGER.warn("Usuario {} id={} desactivado: ya no está en el .env.", rol, u.getId());
        });
    }

    record DatosUsuario(Rol rol, String prefijo, UUID id, String correo, String nombre, String hash) {
        static DatosUsuario validar(Rol rol, String prefijo, String id, String correo, String nombre, String hash) {
            UUID uuid;
            try {
                uuid = UUID.fromString(id == null ? "" : id.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(prefijo + "_ID debe ser un UUID (es el sub del token, no el correo).");
            }
            String normal = Usuario.normalizarCorreo(correo);
            if (normal == null || !normal.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new IllegalStateException(prefijo + "_EMAIL no es un correo válido.");
            }
            if (hash == null || !hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
                throw new IllegalStateException(prefijo + "_PASSWORD_HASH no es un hash BCrypt (genéralo con "
                        + "scripts/verificacion/rotar-password.ps1).");
            }
            String nombreLimpio = nombre == null || nombre.isBlank() ? rol.name() : nombre.trim();
            return new DatosUsuario(rol, prefijo, uuid, normal, nombreLimpio, hash);
        }
    }
}

package com.maxieventos.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maxieventos.auth.entity.Rol;
import com.maxieventos.auth.entity.Usuario;
import com.maxieventos.auth.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Fase 11D: los dos usuarios salen del .env en cada arranque; lo que ya no está en el .env queda inactivo. */
class SembradoUsuariosTest {

    private static final String ADMIN_ID = "7d3f2a1c-5b8e-4c6d-9a0f-1e2b3c4d5e6f";
    private static final String ASIS_ID = "9b1e7c4a-2f3d-4a5b-8c6d-7e8f9a0b1c2d";
    private static final String HASH = new BCryptPasswordEncoder(4).encode("x");
    private static final String HASH_NUEVO = new BCryptPasswordEncoder(4).encode("y");

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final List<Usuario> tabla = new ArrayList<>();

    @BeforeEach
    void tablaEnMemoria() {
        when(repositorio.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            tabla.removeIf(x -> x.getId().equals(u.getId()));
            tabla.add(u);
            return u;
        });
        when(repositorio.findById(any(UUID.class))).thenAnswer(i -> tabla.stream().filter(u -> u.getId().equals(i.getArgument(0))).findFirst());
        when(repositorio.findByCorreo(any(String.class))).thenAnswer(i -> tabla.stream().filter(u -> u.getCorreo().equals(i.getArgument(0))).findFirst());
        when(repositorio.findByRol(any(Rol.class))).thenAnswer(i -> tabla.stream().filter(u -> u.getRol() == i.getArgument(0)).toList());
    }

    private SembradoUsuarios sembrado(String asistenteCorreo, String asistenteHash) {
        return new SembradoUsuarios(repositorio, ADMIN_ID, " Organizadora@Ejemplo.com ", "Organizadora", HASH,
                ASIS_ID, asistenteCorreo, "Asistente de eventos", asistenteHash);
    }

    private Usuario usuario(String id) {
        return tabla.stream().filter(u -> u.getId().toString().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void creaLosDosUsuariosConSuRolYElCorreoNormalizado() {
        sembrado("asistente@ejemplo.com", HASH).run(null);

        assertThat(tabla).hasSize(2);
        assertThat(usuario(ADMIN_ID).getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(usuario(ADMIN_ID).getCorreo()).isEqualTo("organizadora@ejemplo.com");
        assertThat(usuario(ASIS_ID).getRol()).isEqualTo(Rol.ASISTENTE);
        assertThat(usuario(ASIS_ID).getNombre()).isEqualTo("Asistente de eventos");
        assertThat(tabla).allMatch(Usuario::isActivo);
    }

    @Test
    void alRepetirseActualizaElHashSinDuplicar() {
        sembrado("asistente@ejemplo.com", HASH).run(null);
        sembrado("asistente@ejemplo.com", HASH_NUEVO).run(null); // contraseña rotada en el .env

        assertThat(tabla).hasSize(2);
        assertThat(usuario(ASIS_ID).getPasswordHash()).isEqualTo(HASH_NUEVO);
    }

    @Test
    void sinAsistenteEnElEnvElQueExistiaQuedaInactivo() {
        sembrado("asistente@ejemplo.com", HASH).run(null);
        sembrado("", "").run(null);

        assertThat(usuario(ASIS_ID).isActivo()).isFalse();
        assertThat(usuario(ADMIN_ID).isActivo()).isTrue();
    }

    @Test
    void datosInvalidosImpidenArrancarConUnMensajeClaro() {
        assertThatThrownBy(() -> new SembradoUsuarios(repositorio, "no-es-uuid", "a@b.co", "A", HASH, "", "", "", ""))
                .hasMessageContaining("AUTH_ORGANIZER_ID debe ser un UUID");
        assertThatThrownBy(() -> sembrado("asistente@ejemplo.com", "clave-en-claro"))
                .hasMessageContaining("AUTH_ASISTENTE_PASSWORD_HASH no es un hash BCrypt");
        assertThatThrownBy(() -> sembrado("organizadora@ejemplo.com", HASH))
                .hasMessageContaining("no puede ser el mismo correo del administrador");
    }

    @Test
    void unCorreoQueYaUsaOtroIdNoSeSobrescribe() {
        tabla.add(new Usuario(UUID.randomUUID(), "asistente@ejemplo.com", "Otro", Rol.ASISTENTE, HASH));
        assertThatThrownBy(() -> sembrado("asistente@ejemplo.com", HASH).run(null))
                .hasMessageContaining("ya lo usa otro usuario");
        assertThat(repositorio.findByCorreo("asistente@ejemplo.com")).map(Usuario::getNombre).isEqualTo(Optional.of("Otro"));
    }
}

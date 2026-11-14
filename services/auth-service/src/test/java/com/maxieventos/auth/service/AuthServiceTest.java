package com.maxieventos.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maxieventos.auth.dto.LoginRequest;
import com.maxieventos.auth.dto.LoginResponse;
import com.maxieventos.auth.entity.Rol;
import com.maxieventos.auth.entity.Usuario;
import com.maxieventos.auth.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private static final String CORREO = "organizadora@ejemplo.com";
    private static final String CORREO_ASISTENTE = "asistente@ejemplo.com";
    private static final String CLAVE = "Clave.De.Prueba.2026";
    private static final String CLAVE_ASISTENTE = "Otra.Clave.Del.Asistente";
    private static final String SECRETO = "clave-jwt-solo-para-pruebas-unitarias-0123456789";
    private static final UUID ID = UUID.fromString("7d3f2a1c-5b8e-4c6d-9a0f-1e2b3c4d5e6f");
    private static final UUID ID_ASISTENTE = UUID.fromString("9b1e7c4a-2f3d-4a5b-8c6d-7e8f9a0b1c2d");
    private static final String EMISOR = "maxi-eventos-auth";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final AuthService service = new AuthService(repositorio, SECRETO, EMISOR, Duration.ofHours(8), encoder);
    private Usuario asistente;

    @BeforeEach
    void usuarios() {
        when(repositorio.findByCorreo(CORREO)).thenReturn(Optional.of(
                new Usuario(ID, CORREO, "Organizadora", Rol.ADMINISTRADOR, encoder.encode(CLAVE))));
        asistente = new Usuario(ID_ASISTENTE, CORREO_ASISTENTE, "Asistente", Rol.ASISTENTE, encoder.encode(CLAVE_ASISTENTE));
        when(repositorio.findByCorreo(CORREO_ASISTENTE)).thenReturn(Optional.of(asistente));
        when(repositorio.findByCorreo("otra@ejemplo.com")).thenReturn(Optional.empty());
    }

    private static Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
    }

    @Test
    void loginCorrectoDevuelveLosCamposDelContratoYElRolDelAdministrador() {
        LoginResponse respuesta = service.login(new LoginRequest(CORREO, CLAVE));

        assertThat(respuesta.tipoToken()).isEqualTo("Bearer");
        assertThat(respuesta.expiracionEnSegundos()).isEqualTo(28_800L);
        assertThat(respuesta.nombre()).isEqualTo("Organizadora");
        assertThat(respuesta.rol()).isEqualTo("ADMINISTRADOR");
        Claims claims = claims(respuesta.token());
        assertThat(claims.getSubject()).isEqualTo(ID.toString());
        assertThat(claims.getIssuer()).isEqualTo(EMISOR);
        assertThat(claims.get("rol", String.class)).isEqualTo("ADMINISTRADOR");
        // ni el correo ni el nombre viajan en el token (solo está codificado, no cifrado)
        assertThat(claims).doesNotContainValue(CORREO).doesNotContainValue("Organizadora");
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "iss", "iat", "exp", "rol");
        long vigencia = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(vigencia).isEqualTo(respuesta.expiracionEnSegundos());
    }

    @Test
    void elAsistenteRecibeUnTokenConSuRolYSuId() {
        LoginResponse respuesta = service.login(new LoginRequest(CORREO_ASISTENTE, CLAVE_ASISTENTE));

        assertThat(respuesta.rol()).isEqualTo("ASISTENTE");
        assertThat(claims(respuesta.token()).get("rol", String.class)).isEqualTo("ASISTENTE");
        assertThat(claims(respuesta.token()).getSubject()).isEqualTo(ID_ASISTENTE.toString());
    }

    @Test
    void laClaveDeUnUsuarioNoSirveParaElOtro() {
        assertThatThrownBy(() -> service.login(new LoginRequest(CORREO_ASISTENTE, CLAVE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void correoSinDistinguirMayusculasNiEspacios() {
        assertThat(service.login(new LoginRequest("  " + CORREO.toUpperCase() + " ", CLAVE)).token()).isNotBlank();
    }

    @Test
    void claveIncorrectaDevuelve401() {
        assertThatThrownBy(() -> service.login(new LoginRequest(CORREO, "otra-clave")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("Correo o contraseña incorrectos.");
                });
    }

    @Test
    void correoDesconocidoDevuelve401ConElMismoMensaje() {
        assertThatThrownBy(() -> service.login(new LoginRequest("otra@ejemplo.com", CLAVE)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("Correo o contraseña incorrectos.");
                });
    }

    @Test
    void unUsuarioInactivoNoPuedeIniciarSesionAunqueLaClaveSeaCorrecta() {
        asistente.desactivar();
        assertThatThrownBy(() -> service.login(new LoginRequest(CORREO_ASISTENTE, CLAVE_ASISTENTE)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void enmascararNoMuestraElCorreoCompleto() {
        assertThat(AuthService.enmascarar(CORREO)).isEqualTo("o***@ejemplo.com");
        assertThat(AuthService.enmascarar(null)).isEqualTo("(vacío)");
    }
}

package com.maxieventos.auth.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.auth.controller.AuthController;
import com.maxieventos.auth.dto.LoginResponse;
import com.maxieventos.auth.exception.GlobalExceptionHandler;
import com.maxieventos.auth.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Fase 7: con Spring Security completo, el login sigue sin token y sus errores usan el ErrorResponse común. */
@WebMvcTest(controllers = AuthController.class, properties = {
        "auth.jwt.secret=" + SeguridadAuthTest.SECRETO, "auth.jwt.issuer=maxi-eventos-auth"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RespuestasSeguridad.class, GlobalExceptionHandler.class})
class SeguridadAuthTest {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-auth-0123456789";
    private static final String LOGIN = "{\"correo\":\"organizadora@ejemplo.com\",\"password\":\"x\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void loginSinTokenPermitido() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("eyJ...", "Bearer", 28800, "Organizadora", "ADMINISTRADOR"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoToken").value("Bearer"))
                // Fase 11D: el nombre (para la barra) y el rol
                .andExpect(jsonPath("$.nombre").value("Organizadora"))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    @Test
    void credencialesIncorrectas401ConErrorResponse() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void otraRutaSinToken401ConErrorResponse() throws Exception {
        mvc.perform(get("/api/v1/auth/otra"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(RespuestasSeguridad.SIN_SESION));
    }

    @Test
    void tokenDeOtroEmisor401() throws Exception {
        String token = Jwts.builder().subject("3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b").issuer("otro-emisor")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).compact();
        mvc.perform(get("/api/v1/auth/otra").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(RespuestasSeguridad.TOKEN_INVALIDO));
    }
}

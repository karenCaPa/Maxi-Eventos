package com.maxieventos.auditoria.security;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.auditoria.controller.AuditoriaController;
import com.maxieventos.auditoria.service.AuditoriaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Fase 11D, en el propio servicio: la auditoría solo la ve el ADMINISTRADOR (asistente y token sin rol → 403). */
@WebMvcTest(controllers = AuditoriaController.class, properties = {
        "auth.jwt.secret=" + AutorizacionAuditoriaTest.SECRETO, "auth.jwt.issuer=maxi-eventos-auth"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RespuestasSeguridad.class})
class AutorizacionAuditoriaTest {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-auditoria-0123456789";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditoriaService service;

    @Test
    void elAsistenteYUnTokenSinRolNoVenLaAuditoria() throws Exception {
        for (String token : new String[] {token("ASISTENTE"), token(null)}) {
            mvc.perform(get("/api/v1/auditoria").param("eventoId", UUID.randomUUID().toString()).header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                    .andExpect(jsonPath("$.path").value("/api/v1/auditoria"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void elAdministradorVeLaAuditoria() throws Exception {
        mvc.perform(get("/api/v1/auditoria").header("Authorization", token("ADMINISTRADOR"))).andExpect(status().isOk());
    }

    static String token(String rol) {
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).issuer("maxi-eventos-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
        if (rol != null) {
            builder.claim("rol", rol);
        }
        return "Bearer " + builder.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

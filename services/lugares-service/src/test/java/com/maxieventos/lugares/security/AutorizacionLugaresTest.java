package com.maxieventos.lugares.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import com.maxieventos.lugares.controller.LugarController;
import com.maxieventos.lugares.service.LugarService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Fase 11D, en el propio servicio (sin pasar por el gateway): administrar lugares es solo del ADMINISTRADOR. El asistente
 * y un token sin rol (anterior a la 11D) reciben 403 con el ErrorResponse común y el servicio NO se llama; consultar está
 * permitido a los dos roles.
 */
@WebMvcTest(controllers = LugarController.class, properties = {
        "auth.jwt.secret=" + AutorizacionLugaresTest.SECRETO, "auth.jwt.issuer=maxi-eventos-auth"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RespuestasSeguridad.class})
class AutorizacionLugaresTest {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-lugares-0123456789";
    private static final String ID = "8a1f0c2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b";
    private static final String LUGAR = "{\"nombre\":\"Salón\",\"direccion\":\"Calle 1\",\"tipo\":\"SALON\",\"capacidad\":100}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LugarService service;

    @Test
    void elAsistenteYUnTokenSinRolNoAdministranLugares() throws Exception {
        for (String token : new String[] {token("ASISTENTE"), token(null)}) {
            prohibido(post("/api/v1/lugares").contentType(MediaType.APPLICATION_JSON).content(LUGAR), token, "/api/v1/lugares");
            prohibido(put("/api/v1/lugares/" + ID).contentType(MediaType.APPLICATION_JSON).content(LUGAR), token, "/api/v1/lugares/" + ID);
            prohibido(patch("/api/v1/lugares/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"estado\":\"INACTIVO\"}"), token, "/api/v1/lugares/" + ID + "/estado");
            prohibido(delete("/api/v1/lugares/" + ID), token, "/api/v1/lugares/" + ID);
        }
        verifyNoInteractions(service);
    }

    @Test
    void elAsistenteConsultaLugares() throws Exception {
        mvc.perform(get("/api/v1/lugares").header("Authorization", token("ASISTENTE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/lugares/" + ID).header("Authorization", token("ASISTENTE"))).andExpect(status().isOk());
    }

    @Test
    void elAdministradorSiAdministraLugares() throws Exception {
        mvc.perform(patch("/api/v1/lugares/" + ID + "/estado").header("Authorization", token("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"INACTIVO\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/lugares/" + ID).header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
    }

    private void prohibido(MockHttpServletRequestBuilder peticion, String token, String ruta) throws Exception {
        mvc.perform(peticion.header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                .andExpect(jsonPath("$.path").value(ruta));
    }

    /** rol = null: como los tokens emitidos antes de la Fase 11D. */
    static String token(String rol) {
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).issuer("maxi-eventos-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)));
        if (rol != null) {
            builder.claim("rol", rol);
        }
        return "Bearer " + builder.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).compact();
    }
}

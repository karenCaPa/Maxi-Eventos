package com.maxieventos.proveedores.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.proveedores.controller.ProveedorController;
import com.maxieventos.proveedores.dto.EventoProveedorRequest;
import com.maxieventos.proveedores.dto.EventoProveedorResponseDTO;
import com.maxieventos.proveedores.entity.EstadoAsignacion;
import com.maxieventos.proveedores.service.ProveedorService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
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
 * Fase 11D, en el propio servicio: el CATÁLOGO de proveedores (crear, editar, activar / desactivar, borrar) es solo del
 * ADMINISTRADOR; consultarlo y ASIGNAR un proveedor existente a un evento (y confirmar la asignación) lo puede hacer el
 * asistente. Un token sin rol no administra nada.
 */
@WebMvcTest(controllers = ProveedorController.class, properties = {
        "auth.jwt.secret=" + AutorizacionProveedoresTest.SECRETO, "auth.jwt.issuer=maxi-eventos-auth"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RespuestasSeguridad.class})
class AutorizacionProveedoresTest {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-proveedores-0123456789";
    private static final UUID ID = UUID.fromString("0d137724-90c4-40dd-832b-5fc30b37409f");
    private static final UUID EVENTO = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");
    private static final String PROVEEDOR = "{\"nombre\":\"Flores\",\"categoria\":\"FLORISTERIA\",\"contacto\":\"3100000000\"}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProveedorService service;

    @Test
    void elAsistenteYUnTokenSinRolNoAdministranElCatalogo() throws Exception {
        for (String token : new String[] {token("ASISTENTE"), token(null)}) {
            prohibido(post("/api/v1/proveedores").contentType(MediaType.APPLICATION_JSON).content(PROVEEDOR), token,
                    "/api/v1/proveedores");
            prohibido(put("/api/v1/proveedores/" + ID).contentType(MediaType.APPLICATION_JSON).content(PROVEEDOR), token,
                    "/api/v1/proveedores/" + ID);
            prohibido(patch("/api/v1/proveedores/" + ID + "/estado").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"activo\":false}"), token, "/api/v1/proveedores/" + ID + "/estado");
            prohibido(delete("/api/v1/proveedores/" + ID), token, "/api/v1/proveedores/" + ID);
        }
        verifyNoInteractions(service);
    }

    @Test
    void elAsistenteConsultaYAsignaProveedoresAUnEvento() throws Exception {
        String asistente = token("ASISTENTE");
        when(service.asignar(any(EventoProveedorRequest.class), anyString())).thenReturn(new EventoProveedorResponseDTO(
                UUID.randomUUID(), EVENTO, ID, "Sonido", new BigDecimal("900000"), EstadoAsignacion.PENDIENTE));

        mvc.perform(get("/api/v1/proveedores").header("Authorization", asistente)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/proveedores/eventos/" + EVENTO).header("Authorization", asistente)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/proveedores/eventos").header("Authorization", asistente).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"" + EVENTO + "\",\"proveedorId\":\"" + ID
                                + "\",\"servicioAsignado\":\"Sonido\",\"costoAcordado\":900000}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/proveedores/eventos/asignaciones/" + UUID.randomUUID() + "/estado")
                        .header("Authorization", asistente).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"CONFIRMADO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void elAdministradorSiAdministraElCatalogo() throws Exception {
        mvc.perform(patch("/api/v1/proveedores/" + ID + "/estado").header("Authorization", token("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/proveedores/" + ID).header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
    }

    private void prohibido(MockHttpServletRequestBuilder peticion, String token, String ruta) throws Exception {
        mvc.perform(peticion.header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                .andExpect(jsonPath("$.path").value(ruta));
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

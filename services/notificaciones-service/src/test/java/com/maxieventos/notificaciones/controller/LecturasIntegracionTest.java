package com.maxieventos.notificaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.notificaciones.entity.Notificacion;
import com.maxieventos.notificaciones.repository.NotificacionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 11H — la campanita de punta a punta: PostgreSQL 15 real (Testcontainers), la seguridad real (JWT firmados con la
 * clave de la prueba) y la tabla {@code notificaciones_lecturas}. Lo clave: las lecturas son POR USUARIO (sub del JWT).
 * Requiere Docker Desktop en marcha; sin Docker se SALTA.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "auth.jwt.secret=" + LecturasIntegracionTest.SECRETO})
@EmbeddedKafka(partitions = 1)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Campanita: lecturas por usuario (PostgreSQL real + seguridad real)")
class LecturasIntegracionTest {

    static final String SECRETO = "clave-jwt-solo-para-pruebas-de-notificaciones-0123456789";
    private static final String ADMIN = "sub-administradora";
    private static final String ASISTENTE = "sub-asistente";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    MockMvc mvc;
    @Autowired
    NotificacionRepository notificaciones;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;

    private List<Notificacion> creadas;

    @BeforeEach
    void datos() {
        jdbc.update("DELETE FROM notificaciones_lecturas");
        jdbc.update("DELETE FROM notificaciones");
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID evento = UUID.randomUUID();
        creadas = List.of(
                nueva(evento, "Evento confirmado: Boda", ahora.minus(2, ChronoUnit.HOURS)),
                nueva(evento, "Abono registrado", ahora.minus(1, ChronoUnit.HOURS)),
                nueva(evento, "Falta 1 día para su evento: Boda", ahora));
    }

    private Notificacion nueva(UUID evento, String asunto, Instant cuando) {
        return notificaciones.save(new Notificacion(UUID.randomUUID(), evento, "EVENTO_PROXIMO", "cliente:c1", asunto, "Hola.", cuando));
    }

    private static String token(String sub, String rol) {
        var b = Jwts.builder().subject(sub).issuer("maxi-eventos-auth").issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
        if (rol != null) b.claim("rol", rol);
        return "Bearer " + b.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private long sinLeer(String sub, String rol) throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/notificaciones/sin-leer").header("Authorization", token(sub, rol)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).path("sinLeer").asLong();
    }

    private JsonNode recientes(String sub, String rol) throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/notificaciones/recientes").header("Authorization", token(sub, rol)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(cuerpo);
    }

    @Test
    void lasRecientesVanDeLaMasNuevaALaMasAntiguaYSinLeerParaLasDos() throws Exception {
        JsonNode r = recientes(ADMIN, "ADMINISTRADOR");
        assertThat(r.path("totalElementos").asInt()).isEqualTo(3);
        assertThat(r.path("contenido").findValuesAsText("asunto"))
                .containsExactly("Falta 1 día para su evento: Boda", "Abono registrado", "Evento confirmado: Boda");
        assertThat(r.path("contenido").findValuesAsText("leida")).containsOnly("false");
        assertThat(sinLeer(ADMIN, "ADMINISTRADOR")).isEqualTo(3);
        assertThat(sinLeer(ASISTENTE, "ASISTENTE")).isEqualTo(3);
    }

    @Test
    void loQueLeeLaAdministradoraNoCambiaParaLaAsistente() throws Exception {
        UUID id = creadas.get(2).getId();
        mvc.perform(patch("/api/v1/notificaciones/{id}/leida", id).header("Authorization", token(ADMIN, "ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true)).andExpect(jsonPath("$.leidaEn").isNotEmpty());

        assertThat(sinLeer(ADMIN, "ADMINISTRADOR")).isEqualTo(2);
        assertThat(sinLeer(ASISTENTE, "ASISTENTE")).as("la asistente la sigue viendo sin leer").isEqualTo(3);
        assertThat(recientes(ADMIN, "ADMINISTRADOR").path("contenido").get(0).path("leida").asBoolean()).isTrue();
        assertThat(recientes(ASISTENTE, "ASISTENTE").path("contenido").get(0).path("leida").asBoolean()).isFalse();
    }

    @Test
    void marcarDosVecesNoDuplicaYConservaLaPrimeraLectura() throws Exception {
        UUID id = creadas.get(0).getId();
        String primera = mvc.perform(patch("/api/v1/notificaciones/{id}/leida", id).header("Authorization", token(ADMIN, "ADMINISTRADOR")))
                .andReturn().getResponse().getContentAsString();
        String segunda = mvc.perform(patch("/api/v1/notificaciones/{id}/leida", id).header("Authorization", token(ADMIN, "ADMINISTRADOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(segunda).path("leidaEn").asText()).isEqualTo(json.readTree(primera).path("leidaEn").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_lecturas WHERE usuario = ?", Integer.class, ADMIN)).isEqualTo(1);
    }

    @Test
    void marcarTodasEsDelUsuarioActualYLaSegundaVezNoMarcaNada() throws Exception {
        mvc.perform(patch("/api/v1/notificaciones/leidas").header("Authorization", token(ASISTENTE, "ASISTENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(3));
        mvc.perform(patch("/api/v1/notificaciones/leidas").header("Authorization", token(ASISTENTE, "ASISTENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(0));

        assertThat(sinLeer(ASISTENTE, "ASISTENTE")).isZero();
        assertThat(sinLeer(ADMIN, "ADMINISTRADOR")).as("la administradora no cambió").isEqualTo(3);

        // una notificación nueva vuelve a contar como no leída para las dos
        nueva(UUID.randomUUID(), "Abono registrado", Instant.now());
        assertThat(sinLeer(ASISTENTE, "ASISTENTE")).isEqualTo(1);
        assertThat(sinLeer(ADMIN, "ADMINISTRADOR")).isEqualTo(4);
    }

    @Test
    void inexistenteResponde404ConErrorResponseYNoEscribeNada() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/v1/notificaciones/{id}/leida", id).header("Authorization", token(ADMIN, "ADMINISTRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No existe una notificación con id " + id));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_lecturas", Integer.class)).isZero();
    }

    @Test
    void autorizacionSinTokenEs401YCualquierTokenValidoPuedeUsarla() throws Exception {
        mvc.perform(get("/api/v1/notificaciones/sin-leer")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(patch("/api/v1/notificaciones/leidas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notificaciones/sin-leer").header("Authorization", "Bearer no.es.un-token"))
                .andExpect(status().isUnauthorized());
        // los dos roles y un token sin rol (anterior a la 11D): la campanita no exige rol
        assertThat(sinLeer("sub-sin-rol", null)).isEqualTo(3);
    }
}

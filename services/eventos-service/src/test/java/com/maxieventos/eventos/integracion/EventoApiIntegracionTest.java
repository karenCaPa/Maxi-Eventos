package com.maxieventos.eventos.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 8 — integración de eventos-service: la aplicación completa (seguridad JWT, controllers, validación, advice,
 * service, JPA y outbox) contra un PostgreSQL 15 real (Testcontainers, la misma imagen del compose) y los servicios
 * remotos simulados con un servidor HTTP real. Solo Kafka se reemplaza por un doble ({@code KafkaTemplate}): la
 * integración con Kafka se prueba aparte con {@code @EmbeddedKafka}.
 * <p>
 * Requiere Docker Desktop en marcha; sin Docker estas pruebas se SALTAN (no fallan).
 */
@SpringBootTest(properties = {
        "auth.jwt.secret=" + TokensDePrueba.SECRETO,
        // Sin broker en estas pruebas: no se crean los tópicos al arrancar ni corre el reenvío periódico del outbox
        "spring.kafka.admin.auto-create=false",
        "maxi-eventos.outbox.retraso-inicial-ms=3600000",
        "servicios.timeout.lectura=2s"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Integración de eventos-service (PostgreSQL real)")
class EventoApiIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static final ServiciosRemotosSimulados remotos = ServiciosRemotosSimulados.iniciar();

    @DynamicPropertySource
    static void urlsDeLosServicios(DynamicPropertyRegistry registro) {
        for (String servicio : new String[] {"clientes", "lugares", "contratos", "finanzas"}) {
            registro.add("servicios." + servicio + ".url", remotos::url);
        }
    }

    private static final ZoneId NEIVA = ZoneId.of("America/Bogota");
    private static final UUID CLIENTE = UUID.fromString("7a1f2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Fase 11I: reloj del servicio que la prueba puede fijar. Por defecto es el reloj real de Neiva, así que las demás
     * pruebas no cambian; solo la de la fecha pasada lo mueve (y se repone antes de cada prueba).
     */
    static final class RelojAjustable extends Clock {
        private volatile Clock actual = Clock.system(NEIVA);

        void fijarDia(LocalDate dia) {
            actual = Clock.fixed(dia.atTime(10, 0).atZone(NEIVA).toInstant(), NEIVA);
        }

        void reponer() {
            actual = Clock.system(NEIVA);
        }

        @Override
        public ZoneId getZone() {
            return actual.getZone();
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return actual.withZone(zona);
        }

        @Override
        public Instant instant() {
            return actual.instant();
        }
    }

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable();
        }
    }

    @Autowired
    RelojAjustable reloj;

    @BeforeEach
    void limpiar() {
        reloj.reponer();
        jdbc.execute("TRUNCATE eventos, fechas_evento, eventos_mensajes_salientes");
        remotos.reiniciar();
        remotos.responder("/api/v1/clientes/" + CLIENTE, 200, "{\"id\":\"" + CLIENTE + "\",\"nombreCompleto\":\"Sofía Muñoz\"}");
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocacion -> {
            String topico = invocacion.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(
                    new ProducerRecord<>(topico, invocacion.getArgument(1), invocacion.getArgument(2)),
                    new RecordMetadata(new TopicPartition(topico, 0), 0, 0, 0, 0, 0)));
        });
    }

    // ------------------------------------------------------------------ creación

    @Nested
    @DisplayName("POST /api/v1/eventos")
    class Crear {

        @Test
        void happyPath201ConLocationYLaFechaQuedaPendiente() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 2);

            var respuesta = conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                            .content(evento("Quince años de Sofía", sabado)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.nombreEvento").value("Quince años de Sofía"))
                    .andExpect(jsonPath("$.numInvitados").value(110))
                    .andReturn().getResponse();

            String id = json.readTree(respuesta.getContentAsString()).get("id").asText();
            assertThat(respuesta.getHeader("Location")).isEqualTo("http://localhost/api/v1/eventos/" + id);
            assertThat(jdbc.queryForObject("SELECT estado FROM eventos WHERE id = ?::uuid", String.class, id))
                    .isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForMap("SELECT estado, evento_id::text AS evento FROM fechas_evento WHERE fecha = ?", sabado))
                    .containsEntry("estado", "PENDIENTE").containsEntry("evento", id);
            // eventos-service reenvió el JWT de la petición a Clientes
            assertThat(remotos.autorizacionesRecibidas()).singleElement().asString().startsWith("Bearer ey");
        }

        @Test
        void datosInvalidos400ConDetallePorCampo() throws Exception {
            String invalido = """
                    {"nombreEvento":"ab","fecha":"%s","tipoEvento":"BODA","cantidadAdultos":-1,"cantidadNinos":0}
                    """.formatted(proximo(DayOfWeek.SATURDAY, 2));

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(invalido))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Los datos enviados no son válidos. Revisa los campos indicados."))
                    .andExpect(jsonPath("$.path").value("/api/v1/eventos"))
                    .andExpect(jsonPath("$.errores[*].campo").value(hasItems("nombreEvento", "clienteId", "cantidadAdultos", "zona")));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos", Long.class)).isZero();
        }

        @Test
        void valorDeEnumInvalido400ConLosValoresPermitidos() throws Exception {
            String cuerpo = evento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 2)).replace("\"BODA\"", "\"BAUTIZO\"");

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("tipoEvento"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("valor no permitido; los valores válidos son: QUINCE_ANOS, BODA"));
        }

        @Test
        void clienteInexistente404() throws Exception {
            String cuerpo = evento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 2))
                    .replace(CLIENTE.toString(), "00000000-0000-4000-8000-000000000000");

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No existe un cliente con id 00000000-0000-4000-8000-000000000000"));
        }

        @Test
        void otroEventoElMismoFinDeSemana409() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 2);
            crearEvento("Quince años de Sofía", sabado);

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                            .content(evento("Boda de Ana y Luis", sabado.plusDays(1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(startsWith("Ya hay un evento o bloqueo el " + sabado)));
        }

        @Test
        void clientesCaido503() throws Exception {
            remotos.responder("/api/v1/clientes/" + CLIENTE, 500, "{}");

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                            .content(evento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 2))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(
                            "El servicio de clientes no está disponible en este momento. Intenta de nuevo más tarde."));
        }

        @Test
        void unMartes400EnElCampoFechaYNoSeGuardaNada() throws Exception {
            LocalDate martes = proximo(DayOfWeek.TUESDAY, 2);

            conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(evento("Boda de Ana y Luis", martes)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("Solo se pueden registrar eventos en viernes, sábado o domingo."));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fechas_evento", Integer.class)).isZero();
        }
    }

    // ------------------------------------------------------------------ agenda: días permitidos

    @Nested
    @DisplayName("Fechas: solo días permitidos")
    class Fechas {

        @Test
        void lasFechasDisponiblesSoloSonViernesSabadoYDomingo() throws Exception {
            LocalDate lunes = proximo(DayOfWeek.MONDAY, 2);
            conToken(get("/api/v1/fechas/disponibles").param("desde", lunes.toString())
                            .param("hasta", lunes.plusDays(13).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$[0].fecha").value(lunes.plusDays(4).toString()))
                    .andExpect(jsonPath("$[*].diaSemana").value(everyItem(in(List.of("viernes", "sábado", "domingo")))));
        }

        @Test
        void bloquearUnMartes400YLiberarloSePermite() throws Exception {
            LocalDate martes = proximo(DayOfWeek.TUESDAY, 2);
            conToken(put("/api/v1/fechas/{fecha}/estado", martes).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"OCUPADA\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("Solo se pueden registrar eventos en viernes, sábado o domingo."));
            conToken(put("/api/v1/fechas/{fecha}/estado", martes).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"DISPONIBLE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
            conToken(put("/api/v1/fechas/{fecha}/estado", martes.plusDays(4)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"OCUPADA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("OCUPADA"));
        }

        /** Fase 11D: el bloqueo manual de fechas es solo del ADMINISTRADOR, también si se llama directo al servicio. */
        @Test
        void elAsistenteYUnTokenSinRolNoBloqueanFechasPeroSiLasConsultan() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 6);
            for (String token : List.of(TokensDePrueba.asistente(), TokensDePrueba.sinRol())) {
                mvc.perform(put("/api/v1/fechas/{fecha}/estado", sabado).header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"OCUPADA\"}"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                        .andExpect(jsonPath("$.path").value("/api/v1/fechas/" + sabado + "/estado"));
            }
            mvc.perform(get("/api/v1/fechas/disponibles").header("Authorization", TokensDePrueba.asistente())
                            .param("desde", sabado.toString()).param("hasta", sabado.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].fecha").value(sabado.toString())); // sigue libre: no se bloqueó
        }
    }

    // ------------------------------------------------------------------ consulta, paginación y filtros

    @Nested
    @DisplayName("GET /api/v1/eventos")
    class Consultar {

        @Test
        void eventoInexistente404() throws Exception {
            UUID id = UUID.randomUUID();
            conToken(get("/api/v1/eventos/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("No existe un evento con id " + id))
                    .andExpect(jsonPath("$.path").value("/api/v1/eventos/" + id));
        }

        @Test
        void idConFormatoInvalido400() throws Exception {
            conToken(get("/api/v1/eventos/no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("id"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("debe ser un identificador válido (UUID)"));
        }

        @Test
        void paginacionFiltrosYOrden() throws Exception {
            LocalDate primero = proximo(DayOfWeek.SATURDAY, 2);
            crearEvento("Evento uno", primero);
            crearEvento("Evento dos", primero.plusWeeks(1));
            String tres = crearEvento("Evento tres", primero.plusWeeks(2));
            conToken(patch("/api/v1/eventos/{id}/cancelar", tres)).andExpect(status().isOk());

            conToken(get("/api/v1/eventos").param("size", "2").param("page", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido.length()").value(2))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamanoPagina").value(2))
                    .andExpect(jsonPath("$.totalElementos").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andExpect(jsonPath("$.contenido[0].nombreEvento").value("Evento uno"));
            conToken(get("/api/v1/eventos").param("size", "2").param("page", "1"))
                    .andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].nombreEvento").value("Evento tres"));
            conToken(get("/api/v1/eventos").param("sort", "fecha,desc"))
                    .andExpect(jsonPath("$.contenido[0].nombreEvento").value("Evento tres"));
            conToken(get("/api/v1/eventos").param("estado", "CANCELADO"))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(tres));
            conToken(get("/api/v1/eventos").param("estado", "PENDIENTE").param("desde", primero.plusDays(1).toString()))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].nombreEvento").value("Evento dos"));
        }

        @Test
        void paginacionInvalida400() throws Exception {
            conToken(get("/api/v1/eventos").param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("size"));
            conToken(get("/api/v1/eventos").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("page"));
            conToken(get("/api/v1/eventos").param("sort", "clienteId,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("sort"));
        }
    }

    // ------------------------------------------------------------------ confirmación y cancelación

    @Nested
    @DisplayName("PATCH confirmar / cancelar")
    class ConfirmarYCancelar {

        @Test
        void confirmaReservaLaFechaYPublicaEventoConfirmadoTrasElCommit() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 3);
            String id = crearEvento("Boda de Ana y Luis", sabado);
            contratoYBalance(id, "FIRMADO", "1500000", "1500000");

            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("RESERVADO"));

            assertThat(jdbc.queryForObject("SELECT estado FROM fechas_evento WHERE fecha = ?", String.class, sabado))
                    .isEqualTo("OCUPADA");
            // Outbox: la fila se escribió en la transacción y quedó PUBLICADO al confirmar Kafka (clave = eventoId)
            assertThat(jdbc.queryForMap("SELECT tipo, topico, clave, estado FROM eventos_mensajes_salientes"))
                    .containsEntry("tipo", "EVENTO_CONFIRMADO").containsEntry("topico", "eventos.confirmados")
                    .containsEntry("clave", id).containsEntry("estado", "PUBLICADO");
            verify(kafkaTemplate).send(eq("eventos.confirmados"), eq(id), any(JsonNode.class));
        }

        @Test
        void contratoSinFirmar409YNadaCambia() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 3);
            String id = crearEvento("Boda de Ana y Luis", sabado);
            contratoYBalance(id, "PENDIENTE", "1500000", "1500000");

            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "El contrato del evento está en estado PENDIENTE; debe estar FIRMADO para confirmar el evento."));

            assertThat(jdbc.queryForObject("SELECT estado FROM eventos WHERE id = ?::uuid", String.class, id)).isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT estado FROM fechas_evento WHERE fecha = ?", String.class, sabado)).isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_mensajes_salientes", Long.class)).isZero();
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Fase 11I: confirmar con la fecha ya pasada da 409 y nada cambia; el mismo día todavía se confirma")
        void fechaPasada409YElMismoDiaSeConfirma() throws Exception {
            // El evento se crea por la API con una fecha futura; luego el Clock fijo "viaja" al día siguiente del evento.
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 3);
            String id = crearEvento("Boda de Ana y Luis", sabado);
            contratoYBalance(id, "FIRMADO", "1500000", "1500000");

            reloj.fijarDia(sabado.plusDays(1));
            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("No se puede confirmar un evento cuya fecha ya pasó."));
            assertThat(jdbc.queryForObject("SELECT estado FROM eventos WHERE id = ?::uuid", String.class, id)).isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT estado FROM fechas_evento WHERE fecha = ?", String.class, sabado)).isEqualTo("PENDIENTE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_mensajes_salientes", Long.class)).isZero();
            // Solo los envíos de ESTE evento: un envío asíncrono de la prueba anterior puede llegar tarde al mock compartido.
            verify(kafkaTemplate, never()).send(anyString(), eq(id), any());

            // Caso límite: el día del evento todavía no "pasó".
            reloj.fijarDia(sabado);
            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("RESERVADO"));
        }

        @Test
        void abonoInsuficiente409ConElFaltante() throws Exception {
            String id = crearEvento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 3));
            contratoYBalance(id, "FIRMADO", "500000", "1500000");

            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Lo abonado (500000 COP) no alcanza el monto de reserva requerido "
                            + "(1500000 COP). Faltan 1000000 COP."));
        }

        @Test
        void finanzasCaido503() throws Exception {
            String id = crearEvento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 3));
            contratoYBalance(id, "FIRMADO", "1500000", "1500000");
            remotos.responder("/api/v1/eventos/" + id + "/balance", 503, "{}");

            conToken(patch("/api/v1/eventos/{id}/confirmar", id))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503));
        }

        @Test
        void cancelarLiberaLaFechaYCancelarDosVeces409() throws Exception {
            LocalDate sabado = proximo(DayOfWeek.SATURDAY, 3);
            String id = crearEvento("Boda de Ana y Luis", sabado);

            conToken(patch("/api/v1/eventos/{id}/cancelar", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADO"));
            assertThat(jdbc.queryForObject("SELECT estado FROM fechas_evento WHERE fecha = ?", String.class, sabado))
                    .isEqualTo("DISPONIBLE");
            conToken(get("/api/v1/fechas/disponibles").param("desde", sabado.toString()).param("hasta", sabado.toString()))
                    .andExpect(jsonPath("$[*].fecha").value(hasItem(sabado.toString())));

            conToken(patch("/api/v1/eventos/{id}/cancelar", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El evento ya está cancelado."));
        }
    }

    // ------------------------------------------------------------------ seguridad

    @Nested
    @DisplayName("Seguridad")
    class Seguridad {

        @Test
        void sinToken401ConErrorResponse() throws Exception {
            mvc.perform(get("/api/v1/eventos"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(startsWith("Debes iniciar sesión")))
                    .andExpect(jsonPath("$.path").value("/api/v1/eventos"));
            mvc.perform(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON)
                            .content(evento("Boda de Ana y Luis", proximo(DayOfWeek.SATURDAY, 2))))
                    .andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos", Long.class)).isZero();
            assertThat(remotos.autorizacionesRecibidas()).isEmpty();
        }

        @Test
        void tokenVencidoOMalFirmado401() throws Exception {
            mvc.perform(get("/api/v1/eventos").header("Authorization", TokensDePrueba.vencido()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("El token venció. Inicia sesión de nuevo."));
            mvc.perform(get("/api/v1/eventos").header("Authorization", TokensDePrueba.malFirmado()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("El token no es válido. Inicia sesión de nuevo."));
        }

        @Test
        void healthEsPublico() throws Exception {
            mvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    // ------------------------------------------------------------------ utilidades

    private ResultActions conToken(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header("Authorization", TokensDePrueba.valido()));
    }

    private String crearEvento(String nombre, LocalDate fecha) throws Exception {
        String cuerpo = conToken(post("/api/v1/eventos").contentType(MediaType.APPLICATION_JSON).content(evento(nombre, fecha)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asText();
    }

    private static void contratoYBalance(String eventoId, String estadoContrato, String abonado, String reserva) {
        remotos.responder("/api/v1/eventos/" + eventoId + "/contrato", 200,
                "{\"id\":\"" + UUID.randomUUID() + "\",\"eventoId\":\"" + eventoId + "\",\"estado\":\"" + estadoContrato + "\"}");
        remotos.responder("/api/v1/eventos/" + eventoId + "/balance", 200, "{\"eventoId\":\"" + eventoId
                + "\",\"totalAbonado\":" + abonado + ",\"montoReservaRequerido\":" + reserva + ",\"moneda\":\"COP\"}");
    }

    private static String evento(String nombre, LocalDate fecha) {
        return """
                {"nombreEvento":"%s","clienteId":"%s","fecha":"%s","tipoEvento":"BODA",
                 "cantidadAdultos":80,"cantidadNinos":30,"zona":"NEIVA"}
                """.formatted(nombre, CLIENTE, fecha);
    }

    /** El día de la semana indicado, al menos {@code semanas} semanas después de hoy (hora de Colombia). */
    private static LocalDate proximo(DayOfWeek dia, int semanas) {
        return LocalDate.now(NEIVA).plusWeeks(semanas).with(TemporalAdjusters.next(dia));
    }
}

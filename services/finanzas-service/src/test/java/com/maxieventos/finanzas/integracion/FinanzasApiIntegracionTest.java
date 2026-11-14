package com.maxieventos.finanzas.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Fase 8 — integración de finanzas-service: la aplicación completa (seguridad JWT, controllers, validación, advice,
 * services, JPA, bloqueo de la cotización y outbox) contra un PostgreSQL 15 real (Testcontainers, la misma imagen del
 * compose) y eventos-service simulado con un servidor HTTP real. Solo Kafka se reemplaza por un doble.
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
@DisplayName("Integración de finanzas-service (PostgreSQL real)")
class FinanzasApiIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static final ServiciosRemotosSimulados eventos = ServiciosRemotosSimulados.iniciar();

    @DynamicPropertySource
    static void urlDeEventos(DynamicPropertyRegistry registro) {
        registro.add("servicios.eventos.url", eventos::url);
    }

    private static final UUID CLIENTE = UUID.fromString("7a1f2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b");
    /**
     * Boda en Neiva con 80 adultos y 30 niños y las tarifas de application.yml: 12.000.000 de valor base (100 adultos y
     * 10 niños incluidos) + 20 niños adicionales × 50.000 = 13.000.000; reserva del 30 % = 3.900.000.
     */
    private static final BigDecimal TOTAL = new BigDecimal("13000000.00");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    private UUID eventoId;

    @BeforeEach
    void limpiar() {
        jdbc.execute("TRUNCATE pagos, cotizaciones, adicionales_evento, finanzas_mensajes_salientes");
        eventos.reiniciar();
        eventoId = UUID.randomUUID();
        evento(eventoId, "PENDIENTE");
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocacion -> {
            String topico = invocacion.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(
                    new ProducerRecord<>(topico, invocacion.getArgument(1), invocacion.getArgument(2)),
                    new RecordMetadata(new TopicPartition(topico, 0), 0, 0, 0, 0, 0)));
        });
    }

    // ------------------------------------------------------------------ cotización y balance

    @Nested
    @DisplayName("Cotización y balance")
    class CotizacionYBalance {

        @Test
        void cotizacion201ConLosValoresDeLaConfiguracionY200SiYaExiste() throws Exception {
            conToken(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventoId\":\"" + eventoId + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/v1/eventos/" + eventoId + "/cotizacion"))
                    .andExpect(jsonPath("$.tipoEvento").value("BODA"))
                    .andExpect(jsonPath("$.ninosAdicionales").value(20))
                    .andExpect(jsonPath("$.total").value(13000000))
                    .andExpect(jsonPath("$.montoReservaRequerido").value(3900000))
                    .andExpect(jsonPath("$.moneda").value("COP"));

            conToken(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventoId\":\"" + eventoId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(13000000));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cotizaciones", Long.class)).isEqualTo(1);
        }

        @Test
        void balanceSinCotizacion404() throws Exception {
            conToken(get("/api/v1/eventos/{id}/balance", eventoId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("No existe una cotización para el evento " + eventoId));
        }

        @Test
        void cotizarUnEventoInexistente404YEventosCaido503() throws Exception {
            UUID inexistente = UUID.randomUUID();
            conToken(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventoId\":\"" + inexistente + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No existe un evento con id " + inexistente));

            eventos.responder("/api/v1/eventos/" + eventoId, 500, "{}");
            conToken(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventoId\":\"" + eventoId + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503));
        }
    }

    // ------------------------------------------------------------------ pagos

    @Nested
    @DisplayName("POST /api/v1/finanzas/pagos")
    class Pagos {

        @Test
        void happyPath201ActualizaElBalanceYPublicaPagoRegistradoTrasElCommit() throws Exception {
            cotizar(eventoId);

            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Idempotency-Key", "abono-1").content(pago(eventoId, "3900000")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/v1/eventos/" + eventoId + "/pagos"))
                    .andExpect(header().string("X-Already-Processed", "false"))
                    .andExpect(jsonPath("$.monto").value(3900000))
                    .andExpect(jsonPath("$.eventIdempotencia").value("abono-1"));

            conToken(get("/api/v1/eventos/{id}/balance", eventoId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAbonado").value(3900000))
                    .andExpect(jsonPath("$.saldoPendiente").value(9100000))
                    .andExpect(jsonPath("$.montoReservaRequerido").value(3900000));
            assertThat(jdbc.queryForMap("SELECT tipo, topico, clave, estado FROM finanzas_mensajes_salientes"))
                    .containsEntry("tipo", "PAGO_REGISTRADO").containsEntry("topico", "pagos.registrados")
                    .containsEntry("clave", eventoId.toString()).containsEntry("estado", "PUBLICADO");
            verify(kafkaTemplate).send(eq("pagos.registrados"), eq(eventoId.toString()), any(JsonNode.class));
        }

        @Test
        void mismaClaveDeIdempotencia200YUnSoloPago() throws Exception {
            cotizar(eventoId);
            String cuerpo = pago(eventoId, "500000");

            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Idempotency-Key", "abono-repetido").content(cuerpo))
                    .andExpect(status().isCreated());
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                            .header("X-Idempotency-Key", "abono-repetido").content(cuerpo))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Already-Processed", "true"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos", Long.class)).isEqualTo(1);
            verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        }

        @Test
        void datosInvalidos400ConDetallePorCampo() throws Exception {
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clienteId\":\"" + CLIENTE + "\",\"metodoPago\":\" \",\"monto\":-5,\"moneda\":\"COP\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Los datos enviados no son válidos. Revisa los campos indicados."))
                    .andExpect(jsonPath("$.errores[*].campo").value(hasItems("eventoId", "metodoPago", "monto")));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos", Long.class)).isZero();
        }

        @Test
        void monedaDistintaALaDeLaCotizacion400EnElCampo() throws Exception {
            cotizar(eventoId);
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                            .content(pago(eventoId, "100000").replace("\"COP\"", "\"USD\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("moneda"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("debe ser COP, la moneda de la cotización"));
        }

        @Test
        void eventoInexistente404() throws Exception {
            UUID inexistente = UUID.randomUUID();
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(inexistente, "100")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No existe un evento con id " + inexistente));
        }

        @Test
        void montoMayorQueElSaldo409() throws Exception {
            cotizar(eventoId);
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(eventoId, "13000001")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El monto 13000001 supera el saldo pendiente de 13000000.00 COP."));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos", Long.class)).isZero();
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        }

        @Test
        void sinCotizacionOEventoCancelado409() throws Exception {
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(eventoId, "100")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "El evento no tiene cotización. Genera la cotización antes de registrar abonos."));

            UUID cancelado = UUID.randomUUID();
            evento(cancelado, "CANCELADO");
            conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(cancelado, "100")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El evento está CANCELADO y no admite abonos."));
        }

        /**
         * La prueba que justifica PostgreSQL real: dos abonos SIMULTÁNEOS que por separado caben en el saldo pero juntos
         * lo superan. Un trigger (solo en esta prueba) demora 1 s cada INSERT en pagos, para que las dos transacciones
         * se crucen siempre. Con el bloqueo FOR UPDATE de la cotización la segunda espera el commit de la primera, ve el
         * saldo nuevo y recibe 409. Sin el bloqueo, las dos leerían el saldo completo y las dos pagarían (saldo negativo).
         */
        @Test
        void dosAbonosSimultaneosQueJuntosSuperanElSaldoUno201YOtro409() throws Exception {
            cotizar(eventoId);
            jdbc.execute("""
                    CREATE OR REPLACE FUNCTION demorar_insercion_pago() RETURNS trigger AS $$
                    BEGIN PERFORM pg_sleep(1); RETURN NEW; END $$ LANGUAGE plpgsql""");
            jdbc.execute("CREATE TRIGGER demora_pago BEFORE INSERT ON pagos FOR EACH ROW EXECUTE FUNCTION demorar_insercion_pago()");
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch salida = new CountDownLatch(1);
                List<Future<Integer>> respuestas = new ArrayList<>();
                for (String clave : new String[] {"simultaneo-a", "simultaneo-b"}) {
                    Callable<Integer> abono = () -> {
                        salida.await();
                        return conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON)
                                .header("X-Idempotency-Key", clave).content(pago(eventoId, "7000000")))
                                .andReturn().getResponse().getStatus();
                    };
                    respuestas.add(hilos.submit(abono));
                }
                salida.countDown();
                List<Integer> codigos = new ArrayList<>();
                for (Future<Integer> respuesta : respuestas) {
                    codigos.add(respuesta.get(30, TimeUnit.SECONDS));
                }

                assertThat(codigos).containsExactlyInAnyOrder(201, 409);
            } finally {
                hilos.shutdownNow();
                jdbc.execute("DROP TRIGGER IF EXISTS demora_pago ON pagos");
            }
            BigDecimal abonado = jdbc.queryForObject("SELECT coalesce(sum(monto), 0) FROM pagos WHERE evento_id = ?",
                    BigDecimal.class, eventoId);
            assertThat(abonado).isEqualByComparingTo("7000000");
            assertThat(TOTAL.subtract(abonado)).as("el saldo nunca queda negativo").isNotNegative();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finanzas_mensajes_salientes", Long.class)).isEqualTo(1);
            conToken(get("/api/v1/eventos/{id}/balance", eventoId))
                    .andExpect(jsonPath("$.totalAbonado").value(7000000))
                    .andExpect(jsonPath("$.saldoPendiente").value(6000000));
        }
    }

    // ------------------------------------------------------------------ listado de pagos

    @Nested
    @DisplayName("GET /api/v1/eventos/{id}/pagos")
    class ListadoDePagos {

        @Test
        void paginacionOrdenYSoloLosPagosDelEvento() throws Exception {
            cotizar(eventoId);
            UUID otro = UUID.randomUUID();
            evento(otro, "PENDIENTE");
            cotizar(otro);
            for (String monto : new String[] {"1000000", "3000000", "2000000"}) {
                registrarPago(eventoId, monto);
            }
            registrarPago(otro, "500000");

            conToken(get("/api/v1/eventos/{id}/pagos", eventoId).param("size", "2").param("page", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido.length()").value(2))
                    .andExpect(jsonPath("$.totalElementos").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andExpect(jsonPath("$.contenido[0].monto").value(1000000));
            conToken(get("/api/v1/eventos/{id}/pagos", eventoId).param("size", "2").param("page", "1"))
                    .andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].monto").value(2000000));
            conToken(get("/api/v1/eventos/{id}/pagos", eventoId).param("sort", "monto,desc"))
                    .andExpect(jsonPath("$.contenido[0].monto").value(3000000))
                    .andExpect(jsonPath("$.contenido[1].monto").value(2000000))
                    .andExpect(jsonPath("$.contenido[2].monto").value(1000000));
            conToken(get("/api/v1/eventos/{id}/pagos", otro))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].monto").value(500000));
        }

        @Test
        void paginacionInvalida400() throws Exception {
            conToken(get("/api/v1/eventos/{id}/pagos", eventoId).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("size"));
            conToken(get("/api/v1/eventos/{id}/pagos", eventoId).param("sort", "clienteId"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("sort"));
        }
    }

    // ------------------------------------------------------------------ seguridad

    @Nested
    @DisplayName("Seguridad")
    class Seguridad {

        @Test
        void sinToken401ConErrorResponseYSinEfectos() throws Exception {
            mvc.perform(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(eventoId, "100")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value(
                            "Debes iniciar sesión para realizar esta operación. Envía el header Authorization: Bearer <token>."))
                    .andExpect(jsonPath("$.path").value("/api/v1/finanzas/pagos"));
            mvc.perform(get("/api/v1/eventos/{id}/balance", eventoId)).andExpect(status().isUnauthorized());
            assertThat(eventos.autorizacionesRecibidas()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pagos", Long.class)).isZero();
        }

        @Test
        void tokenVencido401() throws Exception {
            mvc.perform(get("/api/v1/eventos/{id}/balance", eventoId).header("Authorization", TokensDePrueba.vencido()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("El token venció. Inicia sesión de nuevo."));
        }

        @Test
        void elTokenSeReenviaAEventos() throws Exception {
            cotizar(eventoId);
            assertThat(eventos.autorizacionesRecibidas()).singleElement().asString().startsWith("Bearer ey");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private ResultActions conToken(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header("Authorization", TokensDePrueba.valido()));
    }

    private void cotizar(UUID evento) throws Exception {
        conToken(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content("{\"eventoId\":\"" + evento + "\"}"))
                .andExpect(status().isCreated());
    }

    private void registrarPago(UUID evento, String monto) throws Exception {
        conToken(post("/api/v1/finanzas/pagos").contentType(MediaType.APPLICATION_JSON).content(pago(evento, monto)))
                .andExpect(status().isCreated());
    }

    /** Evento de eventos-service: boda en Neiva, 80 adultos y 30 niños, del cliente CLIENTE. */
    private static void evento(UUID id, String estado) {
        eventos.responder("/api/v1/eventos/" + id, 200, """
                {"id":"%s","nombreEvento":"Boda de Ana y Luis","clienteId":"%s","tipoEvento":"BODA",
                 "cantidadAdultos":80,"cantidadNinos":30,"zona":"NEIVA","estado":"%s"}
                """.formatted(id, CLIENTE, estado));
    }

    private static String pago(UUID evento, String monto) {
        return """
                {"eventoId":"%s","clienteId":"%s","metodoPago":"TRANSFERENCIA","monto":%s,"moneda":"COP"}
                """.formatted(evento, CLIENTE, monto);
    }
}

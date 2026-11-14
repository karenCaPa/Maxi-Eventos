package com.maxieventos.eventos.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.RecordatorioEnviado;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.entity.ZonaEvento;
import com.maxieventos.eventos.kafka.EventoProximoInterno;
import com.maxieventos.eventos.repository.EventoRepository;
import com.maxieventos.eventos.repository.RecordatorioEnviadoRepository;
import com.maxieventos.eventos.service.RecordatoriosService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 11E — recordatorios EVENTO_PROXIMO contra un PostgreSQL 15 real: qué eventos reciben recordatorio (por umbral y
 * estado), que ejecutar dos veces (o dos a la vez) no duplica nada, el outbox con el idEvento determinista y la clave =
 * eventoId, y que solo el ADMINISTRADOR ejecuta la revisión a mano. Kafka se reemplaza por un doble del KafkaTemplate.
 * Requiere Docker Desktop en marcha; sin Docker se SALTA.
 */
@SpringBootTest(properties = {
        "auth.jwt.secret=" + TokensDePrueba.SECRETO,
        "spring.kafka.admin.auto-create=false",
        "maxi-eventos.outbox.retraso-inicial-ms=3600000",
        // la tarea diaria no debe correr sola durante la prueba
        "maxi-eventos.recordatorios.cron=-",
        "maxi-eventos.recordatorios.dias=7,1"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Recordatorios de eventos próximos (PostgreSQL real)")
class RecordatoriosIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final ZoneId NEIVA = ZoneId.of("America/Bogota");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    EventoRepository eventos;
    @Autowired
    RecordatoriosService servicio;
    @Autowired
    RecordatorioEnviadoRepository enviadosRepo;
    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    private LocalDate hoy;
    private Map<String, Evento> creados;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE eventos, fechas_evento, eventos_mensajes_salientes, eventos_recordatorios_enviados");
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocacion -> {
            String topico = invocacion.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(
                    new ProducerRecord<>(topico, invocacion.getArgument(1), invocacion.getArgument(2)),
                    new RecordMetadata(new TopicPartition(topico, 0), 0, 0, 0, 0, 0)));
        });
        hoy = LocalDate.now(NEIVA);
        creados = Map.of(
                "a8", reservado("A 8 días", 8),
                "a7", reservado("A 7 días", 7),
                "a6", reservado("A 6 días", 6),
                "a1", reservado("A 1 día", 1),
                "hoy", reservado("Hoy", 0),
                "pendiente", evento("Pendiente a 3 días", 3, false),
                "cancelado", cancelado("Cancelado a 3 días", 3));
    }

    private Evento reservado(String nombre, int dias) {
        return evento(nombre, dias, true);
    }

    private Evento evento(String nombre, int dias, boolean confirmar) {
        Evento e = new Evento(nombre, hoy.plusDays(dias), UUID.randomUUID(), TipoEvento.BODA, 80, 10, ZonaEvento.NEIVA, null);
        if (confirmar) {
            e.confirmar();
        }
        return eventos.save(e);
    }

    private Evento cancelado(String nombre, int dias) {
        Evento e = evento(nombre, dias, true);
        e.cancelar();
        return eventos.save(e);
    }

    private JsonNode ejecutar(String token) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/recordatorios/ejecutar").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    /** id del evento → umbral enviado, según la tabla de recordatorios. */
    private Map<UUID, Integer> enviados() {
        return jdbc.queryForList("SELECT evento_id, umbral_dias FROM eventos_recordatorios_enviados").stream()
                .collect(Collectors.toMap(f -> (UUID) f.get("evento_id"), f -> (Integer) f.get("umbral_dias")));
    }

    @Test
    void seleccionaPorUmbralYEstadoYDejaElMensajeEnElOutbox() throws Exception {
        JsonNode r = ejecutar(TokensDePrueba.valido());

        // RESERVADOS entre mañana y +7: los de 7, 6 y 1 día (el de 8 y el de hoy quedan fuera; PENDIENTE y CANCELADO nunca)
        assertThat(r.path("revisados").asInt()).isEqualTo(3);
        assertThat(r.path("enviados").asInt()).isEqualTo(3);
        assertThat(r.path("yaEnviados").asInt()).isZero();
        assertThat(enviados()).containsOnly(
                Map.entry(creados.get("a7").getId(), 7),
                Map.entry(creados.get("a6").getId(), 7),
                Map.entry(creados.get("a1").getId(), 1));

        // Outbox: una fila por recordatorio, tópico eventos.proximos, clave = eventoId, idEvento determinista
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_evento, tipo, topico, clave, payload FROM eventos_mensajes_salientes ORDER BY clave");
        assertThat(filas).hasSize(3).allSatisfy(f -> {
            assertThat(f.get("tipo")).isEqualTo("EVENTO_PROXIMO");
            assertThat(f.get("topico")).isEqualTo("eventos.proximos");
        });
        Evento a6 = creados.get("a6");
        Map<String, Object> fila6 = filas.stream().filter(f -> f.get("clave").equals(a6.getId().toString())).findFirst().orElseThrow();
        assertThat(fila6.get("id_evento")).isEqualTo(EventoProximoInterno.idDeterminista(a6.getId(), 7));
        JsonNode sobre = json.readTree((String) fila6.get("payload"));
        assertThat(sobre.path("eventoId").asText()).isEqualTo(a6.getId().toString());
        assertThat(sobre.path("servicioOrigen").asText()).isEqualTo("eventos-service");
        assertThat(sobre.path("datos").path("diasRestantes").asInt()).isEqualTo(6);
        assertThat(sobre.path("datos").path("umbralDias").asInt()).isEqualTo(7);
        assertThat(sobre.path("datos").path("fecha").asText()).isEqualTo(hoy.plusDays(6).toString());
        assertThat(sobre.path("datos").path("clienteId").asText()).isEqualTo(a6.getClienteId().toString());

        // Se publica después del commit en eventos.proximos con la clave = eventoId
        verify(kafkaTemplate, timeout(5000)).send(eq("eventos.proximos"), eq(a6.getId().toString()), any(JsonNode.class));
        verify(kafkaTemplate, timeout(5000).times(3)).send(eq("eventos.proximos"), anyString(), any(JsonNode.class));
    }

    @Test
    void dosEjecucionesSeguidasNoDuplicanNada() throws Exception {
        ejecutar(TokensDePrueba.valido());
        JsonNode segunda = ejecutar(TokensDePrueba.valido());

        assertThat(segunda.path("enviados").asInt()).isZero();
        assertThat(segunda.path("yaEnviados").asInt()).isEqualTo(3);
        assertThat(segunda.path("recordatorios").findValuesAsText("resultado")).containsOnly("YA_ENVIADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_recordatorios_enviados", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_mensajes_salientes", Integer.class)).isEqualTo(3);
        verify(kafkaTemplate, timeout(5000).times(3)).send(eq("eventos.proximos"), anyString(), any(JsonNode.class));
    }

    /**
     * Fase 11G: la causa del fallo intermitente de dosEjecucionesALaVezTampocoDuplican. Si la otra ejecución ya confirmó la
     * fila, guardar la misma fila otra vez debe CHOCAR con la clave (INSERT), no actualizarla en silencio (merge).
     */
    @Test
    void guardarDosVecesElMismoRecordatorioChocaConLaClave() {
        UUID eventoId = UUID.randomUUID();
        UUID id = EventoProximoInterno.idDeterminista(eventoId, 7);
        enviadosRepo.saveAndFlush(new RecordatorioEnviado(id, eventoId, 7, 5, LocalDate.now(NEIVA).plusDays(5), Instant.now()));

        assertThatThrownBy(() -> enviadosRepo.saveAndFlush(new RecordatorioEnviado(id, eventoId, 7, 5,
                LocalDate.now(NEIVA).plusDays(5), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_recordatorios_enviados WHERE evento_id = ?", Integer.class,
                eventoId)).isEqualTo(1);
    }

    @Test
    void dosEjecucionesALaVezTampocoDuplican() throws Exception {
        var hilos = Executors.newFixedThreadPool(2);
        try {
            var a = hilos.submit(() -> servicio.ejecutar("PRUEBA-A"));
            var b = hilos.submit(() -> servicio.ejecutar("PRUEBA-B"));
            int nuevos = a.get().enviados() + b.get().enviados();
            assertThat(nuevos).as("entre las dos se envían exactamente 3").isEqualTo(3);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_recordatorios_enviados", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_mensajes_salientes", Integer.class)).isEqualTo(3);
    }

    @Test
    void elDeUnDiaSeEnviaAunqueYaSalieraElDeSieteDias() throws Exception {
        Evento a1 = creados.get("a1");
        jdbc.update("INSERT INTO eventos_recordatorios_enviados (id_evento, evento_id, umbral_dias, dias_restantes, fecha_evento, enviado_en)"
                + " VALUES (?, ?, 7, 7, ?, now())", EventoProximoInterno.idDeterminista(a1.getId(), 7), a1.getId(), a1.getFecha());

        ejecutar(TokensDePrueba.valido());

        assertThat(jdbc.queryForList("SELECT umbral_dias FROM eventos_recordatorios_enviados WHERE evento_id = ? ORDER BY umbral_dias",
                Integer.class, a1.getId())).containsExactly(1, 7);
    }

    @Test
    void soloElAdministradorEjecutaLaRevisionAMano() throws Exception {
        for (String token : List.of(TokensDePrueba.asistente(), TokensDePrueba.sinRol())) {
            mvc.perform(post("/api/v1/recordatorios/ejecutar").header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación."))
                    .andExpect(jsonPath("$.path").value("/api/v1/recordatorios/ejecutar"));
        }
        mvc.perform(post("/api/v1/recordatorios/ejecutar")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_recordatorios_enviados", Integer.class)).isZero();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(kafkaTemplate, times(0)).send(eq("eventos.proximos"), anyString(), any());
    }
}

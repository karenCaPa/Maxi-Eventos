package com.maxieventos.notificaciones.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.maxieventos.notificaciones.config.KafkaConsumerConfig;
import com.maxieventos.notificaciones.service.ConsumoNotificacionesService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 11E — consumidor de recordatorios EVENTO_PROXIMO de notificaciones-service con un broker Kafka real embebido
 * ({@code @EmbeddedKafka}) y PostgreSQL 15 real (Testcontainers): el listener, el {@code DefaultErrorHandler}, el DLT y la
 * tabla de idempotencia {@code notificaciones_eventos_procesados} son los de producción. Casos: genera la notificación
 * "Faltan N días…", un duplicado (mismo idEvento) no genera otra, y un mensaje inválido (diasRestantes 0) va al DLT con un
 * solo intento.
 * <p>
 * Requiere Docker Desktop en marcha (PostgreSQL); sin Docker estas pruebas se SALTAN (no fallan).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "auth.jwt.secret=clave-jwt-solo-para-pruebas-de-notificaciones-0123456789"})
@EmbeddedKafka(partitions = 3, topics = {KafkaConsumerConfig.EVENTOS_PROXIMOS_TOPIC})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Integración Kafka de los recordatorios en notificaciones-service (broker embebido + PostgreSQL real)")
class RecordatoriosKafkaIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final String TOPICO = KafkaConsumerConfig.EVENTOS_PROXIMOS_TOPIC;
    private static final String DLT = TOPICO + ".DLT";

    @Autowired
    EmbeddedKafkaBroker broker;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoSpyBean
    ConsumoNotificacionesService consumo;

    private KafkaTemplate<String, String> productor;

    @BeforeEach
    void crearProductor() {
        productor = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void cerrarProductor() {
        productor.destroy();
    }

    @Test
    void generaLaNotificacionYElDuplicadoConElMismoIdEventoSeIgnora(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();
        String mensaje = eventoProximo(idEvento, eventoId, clienteId, 7);

        enviar(eventoId, mensaje);

        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT tipo, destinatario, asunto, estado FROM notificaciones WHERE evento_id = ?", eventoId))
                .singleElement().isEqualTo(Map.of("tipo", "EVENTO_PROXIMO", "destinatario", "cliente:" + clienteId,
                        "asunto", "Faltan 7 días para su evento: Quince años de Sofía", "estado", "GENERADA")));
        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificaciones WHERE evento_id = ?", String.class, eventoId))
                .startsWith("Hola. Faltan 7 días para su evento «Quince años de Sofía» (Quince años) del sábado 5 de "
                        + "diciembre de 2026.");

        // El mismo mensaje otra vez (entrega "al menos una vez"): se reconoce por su idEvento y no genera otra notificación
        enviar(eventoId, mensaje);

        esperarConsumos(salida, idEvento, 2);
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=EVENTO_PROXIMO, eventoId=" + eventoId
                + ", resultado=PROCESADO");
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=EVENTO_PROXIMO, eventoId=" + eventoId
                + ", resultado=DUPLICADO_IGNORADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE evento_id = ?", Long.class, eventoId))
                .as("una sola notificación").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_eventos_procesados WHERE id_evento = ? "
                + "AND consumer_group = 'notificaciones-group'", Long.class, idEvento)).isEqualTo(1);
    }

    @Test
    void elRecordatorioDeUnDiaUsaElSingular() throws Exception {
        UUID eventoId = UUID.randomUUID();
        enviar(eventoId, eventoProximo(UUID.randomUUID(), eventoId, UUID.randomUUID(), 1));

        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT asunto FROM notificaciones WHERE evento_id = ?", String.class, eventoId))
                .containsExactly("Falta 1 día para su evento: Quince años de Sofía"));
    }

    @Test
    void unRecordatorioInvalidoVaAlDltConUnSoloIntento(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();
        // diasRestantes = 0 no cumple el contrato de EVENTO_PROXIMO → MensajeInvalidoException (no reintentable)
        enviar(eventoId, eventoProximo(idEvento, eventoId, UUID.randomUUID(), 0));

        ConsumerRecord<String, byte[]> enDlt = esperarEnDlt(eventoId);

        assertThat(cabecera(enDlt, "kafka_dlt-original-topic")).isEqualTo(TOPICO);
        assertThat(cabecera(enDlt, "kafka_dlt-original-consumer-group")).isEqualTo("notificaciones-group");
        assertThat(cabecera(enDlt, "kafka_dlt-exception-cause-fqcn")).isEqualTo(MensajeInvalidoException.class.getName());
        assertThat(new String(enDlt.value(), StandardCharsets.UTF_8)).contains(idEvento.toString());
        verify(consumo, times(1)).procesarEventoProximo(argThat(registro -> registro.key().equals(eventoId.toString())));
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=EVENTO_PROXIMO, eventoId=" + eventoId
                + ", resultado=INVALIDO_A_DLT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones WHERE evento_id = ?", Long.class, eventoId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificaciones_eventos_procesados WHERE id_evento = ?",
                Long.class, idEvento)).isZero();
    }

    // ------------------------------------------------------------------ utilidades

    private void enviar(UUID clave, String valor) throws Exception {
        productor.send(TOPICO, clave.toString(), valor).get();
    }

    private static void esperarConsumos(CapturedOutput salida, UUID idEvento, int veces) {
        String marca = "idEvento=" + idEvento + ", tipo=";
        await().atMost(ESPERA).until(() -> salida.getOut().split(java.util.regex.Pattern.quote(marca), -1).length - 1 >= veces);
    }

    private ConsumerRecord<String, byte[]> esperarEnDlt(UUID clave) {
        Map<String, Object> propiedades = KafkaTestUtils.consumerProps("prueba-dlt-" + UUID.randomUUID(), "false", broker);
        propiedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, byte[]> lector = new DefaultKafkaConsumerFactory<>(propiedades, new StringDeserializer(),
                new ByteArrayDeserializer()).createConsumer()) {
            lector.subscribe(List.of(DLT));
            List<ConsumerRecord<String, byte[]>> encontrados = new ArrayList<>();
            await().atMost(ESPERA).until(() -> {
                KafkaTestUtils.getRecords(lector, Duration.ofMillis(500)).forEach(registro -> {
                    if (clave.toString().equals(registro.key())) {
                        encontrados.add(registro);
                    }
                });
                return !encontrados.isEmpty();
            });
            assertThat(encontrados).as("el mensaje llega una sola vez al DLT").hasSize(1);
            return encontrados.get(0);
        }
    }

    private static String cabecera(ConsumerRecord<?, ?> registro, String nombre) {
        var cabecera = registro.headers().lastHeader(nombre);
        return cabecera == null ? null : new String(cabecera.value(), StandardCharsets.UTF_8);
    }

    private static String eventoProximo(UUID idEvento, UUID eventoId, UUID clienteId, int dias) {
        return """
                {"idEvento":"%s","tipo":"EVENTO_PROXIMO","version":1,"fechaOcurrencia":"2026-11-28T12:00:00Z",
                 "servicioOrigen":"eventos-service","eventoId":"%s",
                 "datos":{"eventoId":"%s","nombreEvento":"Quince años de Sofía","tipoEvento":"QUINCE_ANOS","fecha":"2026-12-05",
                          "clienteId":"%s","lugarId":"%s","numInvitados":105,"diasRestantes":%d,"umbralDias":%d}}
                """.formatted(idEvento, eventoId, eventoId, clienteId, UUID.randomUUID(), dias, Math.max(dias, 1));
    }
}

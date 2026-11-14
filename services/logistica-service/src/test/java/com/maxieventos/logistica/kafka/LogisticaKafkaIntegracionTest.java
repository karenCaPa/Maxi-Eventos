package com.maxieventos.logistica.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.maxieventos.logistica.config.KafkaConsumerConfig;
import com.maxieventos.logistica.dto.ActualizarItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraResponseDTO;
import com.maxieventos.logistica.service.ConsumoLogisticaService;
import com.maxieventos.logistica.service.LogisticaService;
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
import org.springframework.data.domain.PageRequest;
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
 * Fase 8 — integración del consumidor Kafka de Logística con un broker Kafka real embebido ({@code @EmbeddedKafka}) y
 * PostgreSQL 15 real (Testcontainers): el listener, el {@code DefaultErrorHandler}, el DLT y la tabla de idempotencia
 * {@code logistica_eventos_procesados} son los de producción. Casos de la semana 11: procesa el mensaje, un duplicado
 * (mismo idEvento) no produce un efecto adicional, y un mensaje inválido va al DLT con un solo intento (sin ciclo de
 * reintentos).
 * <p>
 * Requiere Docker Desktop en marcha (PostgreSQL); sin Docker estas pruebas se SALTAN (no fallan).
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "auth.jwt.secret=clave-jwt-solo-para-pruebas-de-logistica-0123456789",
        "spring.data.redis.password=sin-uso-en-pruebas",
        // Sin Redis en esta prueba: la invalidación de la caché falla al instante y el CacheErrorHandler la registra
        "spring.data.redis.port=1"})
@EmbeddedKafka(partitions = 3, topics = {KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC,
        KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Integración Kafka de logistica-service (broker embebido + PostgreSQL real)")
class LogisticaKafkaIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final Duration ESPERA = Duration.ofSeconds(30);
    private static final String DLT = KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC + ".DLT";

    @Autowired
    EmbeddedKafkaBroker broker;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoSpyBean
    ConsumoLogisticaService consumo;
    @Autowired
    LogisticaService logisticaService;

    private KafkaTemplate<String, String> productor;

    @BeforeEach
    void crearProductor() {
        // Productor "crudo" (texto): así se puede enviar también un mensaje que no es JSON
        productor = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void cerrarProductor() {
        productor.destroy();
    }

    @Test
    void procesaEventoConfirmadoYElDuplicadoConElMismoIdEventoSeIgnora(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();
        String mensaje = eventoConfirmado(idEvento, eventoId, 80);

        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, mensaje);

        await().atMost(ESPERA).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT meseros_requeridos, personal_cocina_requerido, camiones_transporte FROM logistica_eventos "
                        + "WHERE evento_id = ?", eventoId))
                .singleElement().isEqualTo(Map.of("meseros_requeridos", 6, "personal_cocina_requerido", 3,
                        "camiones_transporte", 1)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items_compra WHERE evento_id = ?", Long.class, eventoId))
                .isEqualTo(4);
        Map<String, Object> procesado = jdbc.queryForMap("SELECT tipo, offset_kafka FROM logistica_eventos_procesados "
                + "WHERE id_evento = ? AND consumer_group = 'logistica-group'", idEvento);
        assertThat(procesado).containsEntry("tipo", "EVENTO_CONFIRMADO");

        // El mismo mensaje otra vez (entrega "al menos una vez"): se reconoce por su idEvento y no cambia nada
        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, mensaje);

        esperarConsumos(salida, idEvento, 2);
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=EVENTO_CONFIRMADO, eventoId=" + eventoId
                + ", resultado=DUPLICADO_IGNORADO, detalle=idEvento ya procesado por logistica-group");
        assertThat(jdbc.queryForList("SELECT offset_kafka FROM logistica_eventos_procesados WHERE id_evento = ?", idEvento))
                .as("una sola fila de idempotencia, la del primer mensaje").singleElement()
                .isEqualTo(Map.of("offset_kafka", procesado.get("offset_kafka")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM items_compra WHERE evento_id = ?", Long.class, eventoId))
                .as("la lista de compras no se duplica").isEqualTo(4);
    }

    @Test
    void unAdicionalDeTransporteRepetidoNoSumaOtroCamion(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, eventoConfirmado(UUID.randomUUID(), eventoId, 80));
        await().atMost(ESPERA).until(() -> camiones(eventoId) == 1);

        UUID idEvento = UUID.randomUUID();
        String transporte = transporteAdicional(idEvento, eventoId);
        enviar(KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, eventoId, transporte);
        await().atMost(ESPERA).until(() -> camiones(eventoId) == 2);

        enviar(KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, eventoId, transporte);

        esperarConsumos(salida, idEvento, 2);
        assertThat(camiones(eventoId)).as("el duplicado no suma un segundo camión").isEqualTo(2);
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=SERVICIO_ADICIONAL_AGREGADO, eventoId="
                + eventoId + ", resultado=DUPLICADO_IGNORADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM logistica_adicionales WHERE evento_id = ?", Long.class, eventoId))
                .isEqualTo(1);
    }

    /**
     * 10B3 (la prueba clave de la lista de compras): un SERVICIO_ADICIONAL_AGREGADO que recalcula la logística NO borra el
     * ítem manual ni cambia ninguna marca de "conseguido", ni las ediciones de un ítem calculado. Se compara la lista
     * completa (id, nombre, cantidad, comprado, origen y nota) antes y después del recálculo, leída de PostgreSQL.
     */
    @Test
    void elRecalculoPorUnAdicionalConservaLosItemsManualesYLasMarcas(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, eventoConfirmado(UUID.randomUUID(), eventoId, 80));
        await().atMost(ESPERA).until(() -> camiones(eventoId) == 1);

        Map<String, UUID> ids = new java.util.HashMap<>();
        logisticaService.obtenerCompras(eventoId, PageRequest.of(0, 100)).contenido()
                .forEach(item -> ids.put(item.nombreItem(), item.id()));
        assertThat(ids).containsOnlyKeys("Hielo", "Servilletas", "Bebidas", "Vasos");
        // Dos calculados conseguidos, uno calculado editado y un ítem manual conseguido
        logisticaService.actualizarCompras(eventoId, List.of(new ActualizarItemCompraRequest(ids.get("Hielo"), true),
                new ActualizarItemCompraRequest(ids.get("Bebidas"), true)), PageRequest.of(0, 100));
        logisticaService.editarItem(eventoId, ids.get("Servilletas"),
                new ItemCompraRequest("Servilletas de tela", "250 unidades", "Color dorado"));
        ItemCompraResponseDTO recuerdos = logisticaService.agregarItem(eventoId,
                new ItemCompraRequest("Recuerdos para invitados", "80 unidades", "Bolsas con dulces"));
        logisticaService.actualizarCompras(eventoId, List.of(new ActualizarItemCompraRequest(recuerdos.id(), true)),
                PageRequest.of(0, 100));
        List<Map<String, Object>> antes = listaDeCompras(eventoId);
        assertThat(antes).hasSize(5);
        assertThat(antes).filteredOn(fila -> Boolean.TRUE.equals(fila.get("comprado"))).hasSize(3);

        UUID idTransporte = UUID.randomUUID();
        enviar(KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, eventoId, transporteAdicional(idTransporte, eventoId));
        await().atMost(ESPERA).until(() -> camiones(eventoId) == 2);
        esperarConsumos(salida, idTransporte, 1);

        assertThat(salida.getOut()).contains("idEvento=" + idTransporte + ", tipo=SERVICIO_ADICIONAL_AGREGADO, eventoId="
                + eventoId + ", resultado=PROCESADO");
        assertThat(listaDeCompras(eventoId)).as("la lista de compras queda exactamente igual tras el recálculo").isEqualTo(antes);
        assertThat(listaDeCompras(eventoId)).filteredOn(fila -> "MANUAL".equals(fila.get("origen"))).singleElement()
                .satisfies(fila -> {
                    assertThat(fila).containsEntry("nombre_item", "Recuerdos para invitados")
                            .containsEntry("comprado", true).containsEntry("nota", "Bolsas con dulces");
                });
    }

    @Test
    void unMensajeInvalidoVaAlDltConUnSoloIntento(CapturedOutput salida) throws Exception {
        UUID eventoId = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();
        // numInvitados = 0 no cumple el contrato de EVENTO_CONFIRMADO → MensajeInvalidoException (no reintentable)
        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, eventoConfirmado(idEvento, eventoId, 0));

        ConsumerRecord<String, byte[]> enDlt = esperarEnDlt(eventoId);

        assertThat(cabecera(enDlt, "kafka_dlt-original-topic")).isEqualTo(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC);
        assertThat(cabecera(enDlt, "kafka_dlt-original-consumer-group")).isEqualTo("logistica-group");
        assertThat(cabecera(enDlt, "kafka_dlt-exception-cause-fqcn")).isEqualTo(MensajeInvalidoException.class.getName());
        assertThat(new String(enDlt.value(), StandardCharsets.UTF_8)).contains(idEvento.toString());
        // Un solo intento: el listener lo procesó una vez y no hubo ciclo de reintentos
        verify(consumo, times(1)).procesarEventoConfirmado(argThat(registro -> registro.key().equals(eventoId.toString())));
        assertThat(salida.getOut()).contains("idEvento=" + idEvento + ", tipo=EVENTO_CONFIRMADO, eventoId=" + eventoId
                + ", resultado=INVALIDO_A_DLT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM logistica_eventos WHERE evento_id = ?", Long.class, eventoId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM logistica_eventos_procesados WHERE id_evento = ?", Long.class,
                idEvento)).isZero();
    }

    @Test
    void unMensajeQueNoEsJsonVaAlDltConSusBytesOriginales() throws Exception {
        UUID eventoId = UUID.randomUUID();
        enviar(KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, eventoId, "esto no es JSON");

        ConsumerRecord<String, byte[]> enDlt = esperarEnDlt(eventoId);

        assertThat(new String(enDlt.value(), StandardCharsets.UTF_8)).isEqualTo("esto no es JSON");
        assertThat(cabecera(enDlt, "kafka_dlt-exception-fqcn"))
                .isEqualTo("org.springframework.kafka.support.serializer.DeserializationException");
        // Ilegible: falla al deserializar, antes de llegar al listener
        verify(consumo, never()).procesarEventoConfirmado(argThat(registro -> registro.key().equals(eventoId.toString())));
    }

    // ------------------------------------------------------------------ utilidades

    private void enviar(String topico, UUID clave, String valor) throws Exception {
        productor.send(topico, clave.toString(), valor).get();
    }

    /** Espera a que el consumidor registre {@code veces} consumos de ese idEvento (cualquiera sea el resultado). */
    private static void esperarConsumos(CapturedOutput salida, UUID idEvento, int veces) {
        String marca = "idEvento=" + idEvento + ", tipo=";
        await().atMost(ESPERA).until(() -> salida.getOut().split(java.util.regex.Pattern.quote(marca), -1).length - 1 >= veces);
    }

    /** La lista de compras del evento tal como está en la base (todas las columnas que importan), en orden estable. */
    private List<Map<String, Object>> listaDeCompras(UUID eventoId) {
        return jdbc.queryForList("SELECT id, nombre_item, cantidad_estimada, comprado, origen, nota FROM items_compra "
                + "WHERE evento_id = ? ORDER BY id", eventoId);
    }

    private int camiones(UUID eventoId) {
        List<Integer> valor = jdbc.queryForList("SELECT camiones_transporte FROM logistica_eventos WHERE evento_id = ?",
                Integer.class, eventoId);
        return valor.isEmpty() ? 0 : valor.get(0);
    }

    /** Lee el DLT desde el principio hasta encontrar el mensaje con esa clave. */
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

    private static String eventoConfirmado(UUID idEvento, UUID eventoId, int invitados) {
        return """
                {"idEvento":"%s","tipo":"EVENTO_CONFIRMADO","version":1,"fechaOcurrencia":"2026-10-28T10:00:00Z",
                 "servicioOrigen":"eventos-service","eventoId":"%s",
                 "datos":{"eventoId":"%s","nombreEvento":"Quince años de Sofía","tipoEvento":"QUINCE_ANOS","fecha":"2026-12-05",
                          "numInvitados":%d,"cantidadAdultos":%d,"cantidadNinos":0,"zona":"NEIVA"}}
                """.formatted(idEvento, eventoId, eventoId, invitados, invitados);
    }

    private static String transporteAdicional(UUID idEvento, UUID eventoId) {
        return """
                {"idEvento":"%s","tipo":"SERVICIO_ADICIONAL_AGREGADO","version":1,"fechaOcurrencia":"2026-10-28T11:00:00Z",
                 "servicioOrigen":"finanzas-service","eventoId":"%s",
                 "datos":{"adicionalId":"%s","eventoId":"%s","codigo":"transporte-adicional","nombre":"Transporte adicional",
                          "cantidad":1,"subtotal":180000,"moneda":"COP"}}
                """.formatted(idEvento, eventoId, UUID.randomUUID(), eventoId);
    }
}

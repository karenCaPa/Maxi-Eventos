package com.maxieventos.notificaciones.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxieventos.notificaciones.kafka.MensajeInvalidoException;
import com.maxieventos.notificaciones.kafka.RegistroConsumo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de errores del consumidor de notificaciones-service (mismo esquema que logistica-service y contratos-service):
 * <ul>
 *   <li>Errores transitorios (BD no disponible, etc.): {@value #INTENTOS} intentos con 1 s entre ellos y después el DLT.</li>
 *   <li>Mensaje inválido ({@link MensajeInvalidoException}) o ilegible ({@link DeserializationException}): NO se reintenta,
 *       va directo al DLT.</li>
 *   <li>DLT = {@code <tópico>.DLT}, misma partición. Spring agrega las cabeceras {@code kafka_dlt-original-topic},
 *       {@code -original-partition}, {@code -original-offset}, {@code -original-consumer-group}, {@code -exception-message}…</li>
 *   <li>El productor del DLT usa {@link DelegatingByTypeSerializer}: un mensaje ilegible se reenvía con sus bytes
 *       originales y uno válido pero inválido, como JSON.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {
    /** Los declaran sus productores (eventos-service y finanzas-service) y, desde la Fase 11E, también este consumidor. */
    public static final String EVENTOS_CONFIRMADOS_TOPIC = "eventos.confirmados";
    public static final String PAGOS_REGISTRADOS_TOPIC = "pagos.registrados";
    /** Fase 11E: recordatorios de eventos próximos (EVENTO_PROXIMO, productor eventos-service). */
    public static final String EVENTOS_PROXIMOS_TOPIC = "eventos.proximos";
    public static final String GRUPO = "notificaciones-group";
    public static final int INTENTOS = 3;

    // Fase 11E (arreglo del Kafka vacío): el consumidor también declara los tópicos que lee, con 3 particiones (NewTopic es
    // idempotente: si ya existen, no hace nada). KafkaAdmin los crea al arrancar, ANTES de que los listeners se suscriban;
    // con KAFKA_AUTO_CREATE_TOPICS_ENABLE=false el broker ya no crea un tópico de 1 partición al suscribirse.
    @Bean
    NewTopic eventosConfirmadosTopic() {
        return TopicBuilder.name(EVENTOS_CONFIRMADOS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pagosRegistradosTopic() {
        return TopicBuilder.name(PAGOS_REGISTRADOS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eventosProximosTopic() {
        return TopicBuilder.name(EVENTOS_PROXIMOS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eventosConfirmadosDeadLetterTopic() {
        return TopicBuilder.name(EVENTOS_CONFIRMADOS_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pagosRegistradosDeadLetterTopic() {
        return TopicBuilder.name(PAGOS_REGISTRADOS_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eventosProximosDeadLetterTopic() {
        return TopicBuilder.name(EVENTOS_PROXIMOS_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                                          ObjectMapper objectMapper) {
        // Destino explícito: el nombre por defecto de Spring Kafka 3 es "<tópico>-dlt"; el del proyecto es "<tópico>.DLT".
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(plantillaDlt(bootstrapServers, objectMapper),
                (registro, error) -> destinoDlt(registro));
        DefaultErrorHandler handler = new DefaultErrorHandler((registro, error) -> {
            dlt.accept(registro, error);
            RegistroConsumo.registrar(GRUPO, registro, esNoReintentable(error) ? RegistroConsumo.INVALIDO_A_DLT
                    : RegistroConsumo.REINTENTOS_AGOTADOS_A_DLT, causaRaiz(error));
        }, new FixedBackOff(1_000L, INTENTOS - 1L));
        handler.addNotRetryableExceptions(MensajeInvalidoException.class);
        handler.setRetryListeners((registro, error, intento) -> {
            if (!esNoReintentable(error) && intento < INTENTOS) {
                RegistroConsumo.registrar(GRUPO, registro, "REINTENTO " + intento, causaRaiz(error));
            }
        });
        return handler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /** Productor del DLT: bytes originales para lo ilegible, JSON (fechas ISO, sin cabeceras de tipo) para lo demás. */
    static KafkaTemplate<Object, Object> plantillaDlt(String bootstrapServers, ObjectMapper objectMapper) {
        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper.copy().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        json.setAddTypeInfo(false);
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());
        porTipo.put(Object.class, json);
        Map<String, Object> config = Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Serializer<Object> clave = (Serializer) new StringSerializer();
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config, clave, new DelegatingByTypeSerializer(porTipo, true)));
    }

    /** DLT del proyecto: {@code <tópico>.DLT}, misma partición que el mensaje original (los DLT tienen 3 particiones). */
    static TopicPartition destinoDlt(ConsumerRecord<?, ?> registro) {
        return new TopicPartition(registro.topic() + ".DLT", registro.partition());
    }

    static boolean esNoReintentable(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof MensajeInvalidoException || actual instanceof DeserializationException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    static String causaRaiz(Throwable error) {
        Throwable raiz = error;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
    }
}

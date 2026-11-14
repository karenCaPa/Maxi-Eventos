package com.maxieventos.finanzas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaProducerConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /** Tiempo máximo que send() espera los metadatos de Kafka (igual que en eventos-service); sin él serían 60 s. */
    @Value("${maxi-eventos.kafka.max-block-ms:5000}")
    private int maxBlockMs;

    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), serializadorJson(objectMapper));
    }

    /**
     * JSON con fechas ISO-8601 (por defecto el JsonSerializer de Spring Kafka escribe las fechas como números) y sin
     * cabeceras de tipo Java (los consumidores no dependen de las clases del productor).
     */
    public static JsonSerializer<Object> serializadorJson(ObjectMapper base) {
        JsonSerializer<Object> serializador = new JsonSerializer<>(mapperJson(base));
        serializador.setAddTypeInfo(false);
        return serializador;
    }

    /** ObjectMapper de los mensajes: fechas ISO-8601. También lo usa el outbox para guardar el sobre en JSON. */
    public static ObjectMapper mapperJson(ObjectMapper base) {
        return base.copy().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

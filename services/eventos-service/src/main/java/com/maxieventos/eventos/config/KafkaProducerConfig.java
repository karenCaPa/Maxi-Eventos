package com.maxieventos.eventos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Serializador de los mensajes Kafka: JSON con fechas ISO-8601 (por defecto el JsonSerializer de Spring Kafka escribe
 * las fechas como números) y sin cabeceras de tipo Java (los consumidores no dependen de las clases del productor).
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    DefaultKafkaProducerFactoryCustomizer serializadorDeMensajes(ObjectMapper objectMapper) {
        return fabrica -> fabrica.setValueSerializer(serializadorJson(objectMapper));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static JsonSerializer serializadorJson(ObjectMapper base) {
        JsonSerializer<Object> serializador = new JsonSerializer<>(mapperJson(base));
        serializador.setAddTypeInfo(false);
        return serializador;
    }

    /** ObjectMapper de los mensajes: fechas ISO-8601. También lo usa el outbox para guardar el sobre en JSON. */
    public static ObjectMapper mapperJson(ObjectMapper base) {
        return base.copy().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}

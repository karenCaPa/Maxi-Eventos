package com.maxieventos.logistica.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Utilidades para declarar cachés Redis con valores en JSON.
 * Cada caché usa un serializador tipado con la clase exacta de su DTO: así los records (clases final) se
 * deserializan a su tipo correcto sin depender de información de tipo polimórfica.
 */
final class ConfiguracionCache {

    /** ObjectMapper de la caché: JSON legible, fechas ISO-8601 (JavaTimeModule). */
    static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ConfiguracionCache() {
    }

    static JavaType tipo(Class<?> clase) {
        return MAPPER.constructType(clase);
    }

    static JavaType tipoGenerico(Class<?> contenedor, Class<?> parametro) {
        return MAPPER.getTypeFactory().constructParametricType(contenedor, parametro);
    }

    /** Claves: {@code maxi:<servicio>:<cache>::<clave>}; valores: JSON del tipo indicado; sin valores nulos. */
    static RedisCacheConfiguration json(String servicio, Duration ttl, JavaType tipo) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(cache -> "maxi:" + servicio + ":" + cache + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(MAPPER, tipo)));
    }
}

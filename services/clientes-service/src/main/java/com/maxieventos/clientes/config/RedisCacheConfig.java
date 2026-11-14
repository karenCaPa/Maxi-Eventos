package com.maxieventos.clientes.config;

import com.maxieventos.clientes.dto.ClienteResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Caché Redis de clientes-service. Ver la tabla de cachés en el README (sección 7).
 * Se desactiva cuando {@code spring.cache.type} no es {@code redis} (por ejemplo, en la prueba con caché en memoria).
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /** Ficha de un cliente por id. */
    public static final String CLIENTES = "clientes";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${maxi-eventos.cache.ttl.clientes}") Duration ttlClientes) {
        RedisCacheManager redis = RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CLIENTES,
                        ConfiguracionCache.json("clientes", ttlClientes, ConfiguracionCache.tipo(ClienteResponse.class)))
                .disableCreateOnMissingCache()
                .build();
        redis.afterPropertiesSet();
        return new CacheManagerConRegistro(redis);
    }

    @Bean
    public CachingConfigurer configuradorCache() {
        return new CachingConfigurer() {
            @Override
            public CacheErrorHandler errorHandler() {
                return new CacheErrorHandlerConRegistro();
            }
        };
    }
}

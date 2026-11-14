package com.maxieventos.lugares.config;

import com.maxieventos.lugares.dto.LugarResponseDTO;
import com.maxieventos.lugares.dto.PaginaResponse;
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
 * Caché Redis de lugares-service. Ver la tabla de cachés en el README (sección 7).
 * Se desactiva cuando {@code spring.cache.type} no es {@code redis}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /** Ficha de un lugar por id. */
    public static final String LUGARES = "lugares";
    /** Páginas del catálogo de lugares. */
    public static final String LUGARES_LISTADO = "lugares-listado";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${maxi-eventos.cache.ttl.lugares}") Duration ttlLugares,
                                     @Value("${maxi-eventos.cache.ttl.lugares-listado}") Duration ttlListado) {
        RedisCacheManager redis = RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(LUGARES,
                        ConfiguracionCache.json("lugares", ttlLugares, ConfiguracionCache.tipo(LugarResponseDTO.class)))
                .withCacheConfiguration(LUGARES_LISTADO,
                        ConfiguracionCache.json("lugares", ttlListado,
                                ConfiguracionCache.tipoGenerico(PaginaResponse.class, LugarResponseDTO.class)))
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

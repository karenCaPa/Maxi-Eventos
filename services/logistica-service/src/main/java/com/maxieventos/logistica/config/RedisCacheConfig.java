package com.maxieventos.logistica.config;

import com.maxieventos.logistica.dto.RequerimientosLogistica;
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
 * Caché Redis de logistica-service. Ver la tabla de cachés en el README (sección 7).
 * Se desactiva cuando {@code spring.cache.type} no es {@code redis}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /** Requerimientos calculados (meseros, cocina, transporte) de un evento. */
    public static final String LOGISTICA_REQUERIMIENTOS = "logistica-requerimientos";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${maxi-eventos.cache.ttl.logistica-requerimientos}") Duration ttl) {
        RedisCacheManager redis = RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(LOGISTICA_REQUERIMIENTOS,
                        ConfiguracionCache.json("logistica", ttl, ConfiguracionCache.tipo(RequerimientosLogistica.class)))
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

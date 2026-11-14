package com.maxieventos.proveedores.config;

import com.maxieventos.proveedores.dto.PaginaResponse;
import com.maxieventos.proveedores.dto.ProveedorResponseDTO;
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
 * Caché Redis de proveedores-service. Ver la tabla de cachés en el README (sección 7).
 * Se desactiva cuando {@code spring.cache.type} no es {@code redis}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /** Ficha de un proveedor por id. */
    public static final String PROVEEDORES = "proveedores";
    /** Páginas del catálogo de proveedores. */
    public static final String PROVEEDORES_LISTADO = "proveedores-listado";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     @Value("${maxi-eventos.cache.ttl.proveedores}") Duration ttlProveedores,
                                     @Value("${maxi-eventos.cache.ttl.proveedores-listado}") Duration ttlListado) {
        RedisCacheManager redis = RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(PROVEEDORES,
                        ConfiguracionCache.json("proveedores", ttlProveedores, ConfiguracionCache.tipo(ProveedorResponseDTO.class)))
                .withCacheConfiguration(PROVEEDORES_LISTADO,
                        ConfiguracionCache.json("proveedores", ttlListado,
                                ConfiguracionCache.tipoGenerico(PaginaResponse.class, ProveedorResponseDTO.class)))
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

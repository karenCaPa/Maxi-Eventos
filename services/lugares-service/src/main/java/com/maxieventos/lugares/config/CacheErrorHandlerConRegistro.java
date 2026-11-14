package com.maxieventos.lugares.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Si Redis no responde, la aplicación sigue funcionando: el error se registra (sin traza) y la consulta se
 * resuelve contra la base de datos como si fuera un MISS. Las escrituras e invalidaciones fallidas también se
 * registran; al expirar el TTL la caché vuelve a quedar consistente.
 */
public class CacheErrorHandlerConRegistro implements CacheErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("maxieventos.cache");

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object clave) {
        LOGGER.warn("Caché no disponible al leer cache={} clave={}: {} -> se consulta la base de datos",
                cache.getName(), clave, ex.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object clave, Object valor) {
        LOGGER.warn("Caché no disponible al guardar cache={} clave={}: {}", cache.getName(), clave, ex.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object clave) {
        LOGGER.warn("Caché no disponible al invalidar cache={} clave={}: {}", cache.getName(), clave, ex.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        LOGGER.warn("Caché no disponible al limpiar cache={}: {}", cache.getName(), ex.getMessage());
    }
}

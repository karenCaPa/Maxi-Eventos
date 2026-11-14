package com.maxieventos.clientes.config;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Envuelve el {@link CacheManager} de Redis para registrar en el log si cada consulta sale de la caché (HIT)
 * o de la base de datos (MISS). Logger: {@code maxieventos.cache} (nivel configurable en application.yml).
 */
public class CacheManagerConRegistro implements CacheManager {

    static final Logger LOGGER = LoggerFactory.getLogger("maxieventos.cache");

    private final CacheManager delegado;
    private final ConcurrentMap<String, Cache> envueltas = new ConcurrentHashMap<>();

    public CacheManagerConRegistro(CacheManager delegado) {
        this.delegado = delegado;
    }

    @Override
    public Cache getCache(String nombre) {
        Cache cache = delegado.getCache(nombre);
        return cache == null ? null : envueltas.computeIfAbsent(nombre, n -> new CacheConRegistro(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegado.getCacheNames();
    }

    static final class CacheConRegistro implements Cache {

        private final Cache delegado;

        CacheConRegistro(Cache delegado) {
            this.delegado = delegado;
        }

        @Override
        public String getName() {
            return delegado.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegado.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object clave) {
            ValueWrapper valor = delegado.get(clave);
            registrarLectura(clave, valor != null);
            return valor;
        }

        @Override
        public <T> T get(Object clave, Class<T> tipo) {
            T valor = delegado.get(clave, tipo);
            registrarLectura(clave, valor != null);
            return valor;
        }

        @Override
        public <T> T get(Object clave, Callable<T> cargador) {
            return delegado.get(clave, cargador);
        }

        @Override
        public CompletableFuture<?> retrieve(Object clave) {
            return delegado.retrieve(clave);
        }

        @Override
        public <T> CompletableFuture<T> retrieve(Object clave, Supplier<CompletableFuture<T>> cargador) {
            return delegado.retrieve(clave, cargador);
        }

        @Override
        public void put(Object clave, Object valor) {
            delegado.put(clave, valor);
            LOGGER.info("Caché PUT   cache={} clave={}", getName(), clave);
        }

        @Override
        public ValueWrapper putIfAbsent(Object clave, Object valor) {
            return delegado.putIfAbsent(clave, valor);
        }

        @Override
        public void evict(Object clave) {
            delegado.evict(clave);
            LOGGER.info("Caché EVICT cache={} clave={}", getName(), clave);
        }

        @Override
        public boolean evictIfPresent(Object clave) {
            boolean presente = delegado.evictIfPresent(clave);
            LOGGER.info("Caché EVICT cache={} clave={}", getName(), clave);
            return presente;
        }

        @Override
        public void clear() {
            delegado.clear();
            LOGGER.info("Caché CLEAR cache={} (se invalidan todas las entradas)", getName());
        }

        @Override
        public boolean invalidate() {
            boolean habia = delegado.invalidate();
            LOGGER.info("Caché CLEAR cache={} (se invalidan todas las entradas)", getName());
            return habia;
        }

        private void registrarLectura(Object clave, boolean encontrado) {
            if (encontrado) {
                LOGGER.info("Caché HIT   cache={} clave={}", getName(), clave);
            } else {
                LOGGER.info("Caché MISS  cache={} clave={} -> se consulta la base de datos", getName(), clave);
            }
        }
    }
}

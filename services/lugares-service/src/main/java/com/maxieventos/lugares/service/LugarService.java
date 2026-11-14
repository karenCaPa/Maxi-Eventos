package com.maxieventos.lugares.service;

import com.maxieventos.lugares.config.RedisCacheConfig;
import com.maxieventos.lugares.dto.LugarRequest;
import com.maxieventos.lugares.dto.LugarResponseDTO;
import com.maxieventos.lugares.dto.PaginaResponse;
import com.maxieventos.lugares.entity.EstadoLugar;
import com.maxieventos.lugares.entity.Lugar;
import com.maxieventos.lugares.exception.RecursoNoEncontradoException;
import com.maxieventos.lugares.repository.LugarFiltros;
import com.maxieventos.lugares.repository.LugarRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LugarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LugarService.class);

    private final LugarRepository repository;

    public LugarService(LugarRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.LUGARES_LISTADO, key = "#nombre + ':' + #tipo + ':' + #estado + ':'"
            + " + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public PaginaResponse<LugarResponseDTO> listar(String nombre, String tipo, EstadoLugar estado, Pageable pageable) {
        return PaginaResponse.de(repository.findAll(LugarFiltros.de(nombre, tipo, estado), pageable),
                LugarResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.LUGARES, key = "#id")
    public LugarResponseDTO obtener(UUID id) {
        return LugarResponseDTO.from(buscar(id));
    }

    @Transactional
    @CacheEvict(value = RedisCacheConfig.LUGARES_LISTADO, allEntries = true)
    public LugarResponseDTO crear(LugarRequest request) {
        Lugar lugar = repository.save(new Lugar(request.nombre(), request.direccion(), request.tipo(), request.capacidad()));
        LOGGER.info("Lugar creado. id={}", lugar.getId());
        return LugarResponseDTO.from(lugar);
    }

    @Transactional
    @Caching(put = @CachePut(value = RedisCacheConfig.LUGARES, key = "#id"),
            evict = @CacheEvict(value = RedisCacheConfig.LUGARES_LISTADO, allEntries = true))
    public LugarResponseDTO actualizar(UUID id, LugarRequest request) {
        Lugar lugar = buscar(id);
        lugar.actualizar(request.nombre(), request.direccion(), request.tipo(), request.capacidad());
        LOGGER.info("Lugar actualizado. id={}", id);
        return LugarResponseDTO.from(repository.save(lugar));
    }

    /**
     * El lugar cacheado se reemplaza por el nuevo estado ({@code @CachePut}): eventos-service consulta
     * {@code GET /lugares/{id}} y debe ver el estado actual para rechazar un lugar INACTIVO.
     */
    @Transactional
    @Caching(put = @CachePut(value = RedisCacheConfig.LUGARES, key = "#id"),
            evict = @CacheEvict(value = RedisCacheConfig.LUGARES_LISTADO, allEntries = true))
    public LugarResponseDTO cambiarEstado(UUID id, EstadoLugar estado) {
        Lugar lugar = buscar(id);
        lugar.cambiarEstado(estado);
        LOGGER.info("Lugar {}. id={}", estado == EstadoLugar.ACTIVO ? "activado" : "inactivado", id);
        return LugarResponseDTO.from(repository.save(lugar));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisCacheConfig.LUGARES, key = "#id"),
            @CacheEvict(value = RedisCacheConfig.LUGARES_LISTADO, allEntries = true)})
    public void eliminar(UUID id) {
        repository.delete(buscar(id));
        LOGGER.info("Lugar eliminado. id={}", id);
    }

    private Lugar buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un lugar con id " + id));
    }
}

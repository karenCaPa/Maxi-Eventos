package com.maxieventos.proveedores.service;

import com.maxieventos.proveedores.config.RedisCacheConfig;
import com.maxieventos.proveedores.dto.EventoProveedorRequest;
import com.maxieventos.proveedores.dto.EventoProveedorResponseDTO;
import com.maxieventos.proveedores.dto.PaginaResponse;
import com.maxieventos.proveedores.dto.ProveedorRequest;
import com.maxieventos.proveedores.dto.ProveedorResponseDTO;
import com.maxieventos.proveedores.entity.EstadoAsignacion;
import com.maxieventos.proveedores.entity.EventoProveedor;
import com.maxieventos.proveedores.entity.Proveedor;
import com.maxieventos.proveedores.exception.RecursoNoEncontradoException;
import com.maxieventos.proveedores.exception.ReglaNegocioException;
import com.maxieventos.proveedores.repository.EventoProveedorRepository;
import com.maxieventos.proveedores.repository.ProveedorFiltros;
import com.maxieventos.proveedores.repository.ProveedorRepository;
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
public class ProveedorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProveedorService.class);

    private final ProveedorRepository proveedorRepository;
    private final EventoProveedorRepository asignacionRepository;
    private final EventoServiceClient eventoServiceClient;

    public ProveedorService(ProveedorRepository proveedorRepository, EventoProveedorRepository asignacionRepository,
                            EventoServiceClient eventoServiceClient) {
        this.proveedorRepository = proveedorRepository;
        this.asignacionRepository = asignacionRepository;
        this.eventoServiceClient = eventoServiceClient;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.PROVEEDORES_LISTADO, key = "#categoria + ':' + #activo + ':'"
            + " + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public PaginaResponse<ProveedorResponseDTO> listar(String categoria, Boolean activo, Pageable pageable) {
        return PaginaResponse.de(proveedorRepository.findAll(ProveedorFiltros.de(categoria, activo), pageable),
                ProveedorResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.PROVEEDORES, key = "#id")
    public ProveedorResponseDTO obtener(UUID id) {
        return ProveedorResponseDTO.from(obtenerProveedor(id));
    }

    @Transactional
    @CacheEvict(value = RedisCacheConfig.PROVEEDORES_LISTADO, allEntries = true)
    public ProveedorResponseDTO crear(ProveedorRequest request) {
        String nombre = request.nombre().trim();
        if (proveedorRepository.existeConNombre(nombre)) {
            throw duplicado(nombre);
        }
        Proveedor proveedor = proveedorRepository.save(new Proveedor(nombre, request.categoria(), request.contacto()));
        LOGGER.info("Proveedor creado. id={}", proveedor.getId());
        return ProveedorResponseDTO.from(proveedor);
    }

    @Transactional
    @Caching(put = @CachePut(value = RedisCacheConfig.PROVEEDORES, key = "#id"),
            evict = @CacheEvict(value = RedisCacheConfig.PROVEEDORES_LISTADO, allEntries = true))
    public ProveedorResponseDTO actualizar(UUID id, ProveedorRequest request) {
        Proveedor proveedor = obtenerProveedor(id);
        String nombre = request.nombre().trim();
        if (proveedorRepository.existeOtroConNombre(nombre, id)) {
            throw duplicado(nombre);
        }
        proveedor.actualizar(nombre, request.categoria(), request.contacto());
        LOGGER.info("Proveedor actualizado. id={}", id);
        return ProveedorResponseDTO.from(proveedorRepository.save(proveedor));
    }

    @Transactional
    @Caching(put = @CachePut(value = RedisCacheConfig.PROVEEDORES, key = "#id"),
            evict = @CacheEvict(value = RedisCacheConfig.PROVEEDORES_LISTADO, allEntries = true))
    public ProveedorResponseDTO cambiarEstado(UUID id, boolean activo) {
        Proveedor proveedor = obtenerProveedor(id);
        proveedor.setActivo(activo);
        LOGGER.info("Proveedor {}. id={}", activo ? "activado" : "desactivado", id);
        return ProveedorResponseDTO.from(proveedorRepository.save(proveedor));
    }

    /** Solo se borra un proveedor sin asignaciones; si tiene, se debe inactivar para conservar el historial. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = RedisCacheConfig.PROVEEDORES, key = "#id"),
            @CacheEvict(value = RedisCacheConfig.PROVEEDORES_LISTADO, allEntries = true)})
    public void eliminar(UUID id) {
        Proveedor proveedor = obtenerProveedor(id);
        if (asignacionRepository.existsByProveedorId(id)) {
            throw new ReglaNegocioException("El proveedor " + proveedor.getNombre()
                    + " tiene asignaciones a eventos y no se puede eliminar. Inactívalo en su lugar.");
        }
        proveedorRepository.delete(proveedor);
        LOGGER.info("Proveedor eliminado. id={}", id);
    }

    /**
     * Las asignaciones de un evento no se cachean: cambian al planear el evento. Primero se valida contra Eventos
     * que el evento exista (404 si no; 503 si Eventos no responde); un evento sin asignaciones devuelve página vacía.
     */
    public PaginaResponse<EventoProveedorResponseDTO> listarAsignaciones(UUID eventoId, Pageable pageable,
                                                                       String authorization) {
        eventoServiceClient.validarEvento(eventoId, authorization);
        return PaginaResponse.de(asignacionRepository.findByEventoId(eventoId, pageable), EventoProveedorResponseDTO::from);
    }

    @Transactional
    public EventoProveedorResponseDTO asignar(EventoProveedorRequest request, String authorization) {
        eventoServiceClient.validarEvento(request.eventoId(), authorization);
        Proveedor proveedor = obtenerProveedor(request.proveedorId());
        if (!proveedor.isActivo()) {
            throw new ReglaNegocioException("El proveedor está inactivo y no se puede asignar a un evento");
        }
        // Misma regla que la restricción UNIQUE de la tabla (evento + proveedor + servicio), con un mensaje claro: el
        // proveedor sí puede asignarse al mismo evento para otro servicio. La UNIQUE queda como respaldo ante dos
        // peticiones simultáneas (el advice la traduce a 409 genérico).
        if (asignacionRepository.existsByEventoIdAndProveedorIdAndServicioAsignado(request.eventoId(),
                request.proveedorId(), request.servicioAsignado())) {
            throw new ReglaNegocioException("El proveedor «" + proveedor.getNombre()
                    + "» ya está asignado a este evento para el servicio «" + request.servicioAsignado() + "»");
        }
        EstadoAsignacion estado = request.estado() == null ? EstadoAsignacion.PENDIENTE : request.estado();
        EventoProveedor asignacion = asignacionRepository.saveAndFlush(new EventoProveedor(request.eventoId(),
                request.proveedorId(), request.servicioAsignado(), request.costoAcordado(), estado));
        LOGGER.info("Proveedor asignado a evento. asignacionId={}, eventoId={}, proveedorId={}",
                asignacion.getId(), request.eventoId(), request.proveedorId());
        return EventoProveedorResponseDTO.from(asignacion);
    }

    /** Solo se confirma una asignación pendiente (PENDIENTE → CONFIRMADO); cualquier otro cambio es 409. */
    @Transactional
    public EventoProveedorResponseDTO cambiarEstadoAsignacion(UUID asignacionId, EstadoAsignacion nuevoEstado) {
        EventoProveedor asignacion = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una asignación con id " + asignacionId));
        if (nuevoEstado != EstadoAsignacion.CONFIRMADO) {
            throw new ReglaNegocioException("Una asignación solo se puede pasar de pendiente a confirmada; se pidió dejarla "
                    + etiquetaAsignacion(nuevoEstado) + ".");
        }
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new ReglaNegocioException("Solo se puede confirmar una asignación pendiente; esta ya está "
                    + etiquetaAsignacion(asignacion.getEstado()) + ".");
        }
        asignacion.cambiarEstado(nuevoEstado);
        LOGGER.info("Asignación confirmada. asignacionId={}, eventoId={}", asignacionId, asignacion.getEventoId());
        return EventoProveedorResponseDTO.from(asignacionRepository.save(asignacion));
    }

    private static String etiquetaAsignacion(EstadoAsignacion estado) {
        return switch (estado) {
            case PENDIENTE -> "pendiente";
            case CONFIRMADO -> "confirmada";
            case CANCELADO -> "cancelada";
        };
    }

    private Proveedor obtenerProveedor(UUID id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un proveedor con id " + id));
    }

    private static ReglaNegocioException duplicado(String nombre) {
        return new ReglaNegocioException("Ya existe un proveedor con el nombre " + nombre);
    }
}

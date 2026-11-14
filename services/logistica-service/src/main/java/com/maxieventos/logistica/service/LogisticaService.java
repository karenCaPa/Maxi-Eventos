package com.maxieventos.logistica.service;

import com.maxieventos.logistica.config.RedisCacheConfig;
import com.maxieventos.logistica.dto.ActualizarItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraResponseDTO;
import com.maxieventos.logistica.dto.PaginaResponse;
import com.maxieventos.logistica.dto.RequerimientosLogistica;
import com.maxieventos.logistica.entity.ItemCompraEntity;
import com.maxieventos.logistica.entity.LogisticaEvento;
import com.maxieventos.logistica.exception.RecursoNoEncontradoException;
import com.maxieventos.logistica.exception.ReglaNegocioException;
import com.maxieventos.logistica.repository.ItemCompraRepository;
import com.maxieventos.logistica.repository.LogisticaEventoRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API REST de logística. El cálculo a partir de los mensajes Kafka está en {@link ConsumoLogisticaService}. */
@Service
public class LogisticaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogisticaService.class);
    private final LogisticaEventoRepository logisticaRepository;
    private final ItemCompraRepository comprasRepository;

    public LogisticaService(LogisticaEventoRepository logisticaRepository, ItemCompraRepository comprasRepository) {
        this.logisticaRepository = logisticaRepository;
        this.comprasRepository = comprasRepository;
    }

    /** Cacheado: se invalida cuando un servicio adicional recalcula la logística del evento. */
    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#eventoId")
    public RequerimientosLogistica obtenerRequerimientos(UUID eventoId) {
        LogisticaEvento evento = buscarEvento(eventoId);
        return new RequerimientosLogistica(evento.getEventoId(), evento.getMeserosRequeridos(),
                evento.getPersonalCocinaRequerido(), evento.getCamionesTransporte());
    }

    /** Las compras no se cachean: se marcan como compradas constantemente. */
    @Transactional(readOnly = true)
    public PaginaResponse<ItemCompraResponseDTO> obtenerCompras(UUID eventoId, Pageable pageable) {
        buscarEvento(eventoId);
        return PaginaResponse.de(comprasRepository.findAllByLogisticaEventoEventoId(eventoId, pageable),
                ItemCompraResponseDTO::from);
    }

    /**
     * Marca los ítems y devuelve la página de compras ya actualizada. Un ítem que no existe o que es de otro
     * evento → 404 y no se aplica ningún cambio (la transacción se revierte).
     */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#eventoId")
    public PaginaResponse<ItemCompraResponseDTO> actualizarCompras(UUID eventoId,
                                                                   List<ActualizarItemCompraRequest> solicitudes,
                                                                   Pageable pageable) {
        buscarEvento(eventoId);
        for (ActualizarItemCompraRequest solicitud : solicitudes) {
            ItemCompraEntity item = comprasRepository
                    .findByIdAndLogisticaEventoEventoId(solicitud.id(), eventoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No existe el ítem de compra " + solicitud.id() + " en el evento " + eventoId));
            item.setComprado(solicitud.comprado());
        }
        comprasRepository.flush();
        LOGGER.info("Compras actualizadas. eventoId={}, items={}", eventoId, solicitudes.size());
        return PaginaResponse.de(comprasRepository.findAllByLogisticaEventoEventoId(eventoId, pageable),
                ItemCompraResponseDTO::from);
    }

    // ------------------------------------------------------------------ 10B3: ítems manuales
    // Cada cambio invalida la caché de logística del evento (@CacheEvict, después del commit). Las compras en sí no se
    // cachean; se invalida igual para que ningún dato de logística del evento quede viejo.

    /** Agrega un ítem MANUAL (sin marcar). Nombre repetido en el evento (sin distinguir mayúsculas) → 409. */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#eventoId")
    public ItemCompraResponseDTO agregarItem(UUID eventoId, ItemCompraRequest request) {
        LogisticaEvento evento = buscarEvento(eventoId);
        String nombre = request.nombreItem().trim();
        if (comprasRepository.existeNombreEnEvento(eventoId, nombre)) {
            throw nombreRepetido(nombre);
        }
        ItemCompraEntity item = ItemCompraEntity.manual(nombre, request.cantidadEstimada().trim(), notaLimpia(request.nota()));
        evento.agregarCompra(item);
        comprasRepository.saveAndFlush(item);
        LOGGER.info("Ítem de compra agregado. eventoId={}, itemId={}", eventoId, item.getId());
        return ItemCompraResponseDTO.from(item);
    }

    /** Edita nombre, cantidad y nota de cualquier ítem (calculado o manual); conserva su origen y su marca de comprado. */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#eventoId")
    public ItemCompraResponseDTO editarItem(UUID eventoId, UUID itemId, ItemCompraRequest request) {
        buscarEvento(eventoId);
        ItemCompraEntity item = buscarItem(eventoId, itemId);
        String nombre = request.nombreItem().trim();
        if (comprasRepository.existeOtroConNombreEnEvento(eventoId, nombre, itemId)) {
            throw nombreRepetido(nombre);
        }
        item.editar(nombre, request.cantidadEstimada().trim(), notaLimpia(request.nota()));
        comprasRepository.flush();
        LOGGER.info("Ítem de compra editado. eventoId={}, itemId={}", eventoId, itemId);
        return ItemCompraResponseDTO.from(item);
    }

    /** Quita un ítem MANUAL. Los calculados por el sistema no se quitan (409): se pueden editar o dejar sin marcar. */
    @Transactional
    @CacheEvict(value = RedisCacheConfig.LOGISTICA_REQUERIMIENTOS, key = "#eventoId")
    public void quitarItem(UUID eventoId, UUID itemId) {
        buscarEvento(eventoId);
        ItemCompraEntity item = buscarItem(eventoId, itemId);
        if (!item.esManual()) {
            throw new ReglaNegocioException(
                    "Los ítems calculados por el sistema no se pueden quitar; puedes editarlos o dejarlos sin marcar.");
        }
        comprasRepository.delete(item);
        LOGGER.info("Ítem de compra quitado. eventoId={}, itemId={}", eventoId, itemId);
    }

    private ItemCompraEntity buscarItem(UUID eventoId, UUID itemId) {
        return comprasRepository.findByIdAndLogisticaEventoEventoId(itemId, eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el ítem de compra " + itemId + " en el evento " + eventoId));
    }

    private static ReglaNegocioException nombreRepetido(String nombre) {
        return new ReglaNegocioException("Ya hay un ítem «" + nombre + "» en la lista de compras de este evento.");
    }

    private static String notaLimpia(String nota) {
        return nota == null || nota.isBlank() ? null : nota.trim();
    }

    private LogisticaEvento buscarEvento(UUID eventoId) {
        return logisticaRepository.findById(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe logística para el evento " + eventoId));
    }
}

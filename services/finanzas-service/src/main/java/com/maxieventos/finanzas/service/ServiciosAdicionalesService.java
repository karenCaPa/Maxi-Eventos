package com.maxieventos.finanzas.service;

import com.maxieventos.finanzas.dto.AdicionalCatalogoResponse;
import com.maxieventos.finanzas.dto.AdicionalEventoResponseDTO;
import com.maxieventos.finanzas.dto.AdicionalRequest;
import com.maxieventos.finanzas.entity.AdicionalEvento;
import com.maxieventos.finanzas.exception.RecursoNoEncontradoException;
import com.maxieventos.finanzas.exception.SolicitudInvalidaException;
import com.maxieventos.finanzas.kafka.MensajeInterno;
import com.maxieventos.finanzas.repository.AdicionalEventoRepository;
import com.maxieventos.finanzas.repository.CotizacionRepository;
import com.maxieventos.finanzas.service.remoto.EventosClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiciosAdicionalesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiciosAdicionalesService.class);

    private final AdicionalEventoRepository adicionalRepository;
    private final CotizacionRepository cotizacionRepository;
    private final EventosClient eventosClient;
    private final ApplicationEventPublisher publicador;
    private final Map<String, AdicionalCatalogoResponse> catalog;

    public ServiciosAdicionalesService(AdicionalEventoRepository adicionalRepository,
            CotizacionRepository cotizacionRepository, CotizacionService cotizacionService, EventosClient eventosClient,
            ApplicationEventPublisher publicador,
            @Value("${finanzas.adicionales.decoracion-premium}") BigDecimal decoracion,
            @Value("${finanzas.adicionales.hora-extra}") BigDecimal horaExtra,
            @Value("${finanzas.adicionales.transporte-adicional}") BigDecimal transporte) {
        this.adicionalRepository = adicionalRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.eventosClient = eventosClient;
        this.publicador = publicador;
        this.catalog = cotizacionService.catalogo(decoracion, horaExtra, transporte).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        AdicionalCatalogoResponse::codigo, item -> item));
    }

    /** No se cachea en Redis: se calcula en memoria a partir de la configuración. */
    public List<AdicionalCatalogoResponse> catalogo() {
        return List.copyOf(catalog.values());
    }

    /**
     * Servicios adicionales de un evento, en el orden en que se agregaron.
     * 404 si el evento no existe; lista vacía si no tiene; 503 si eventos-service no responde.
     */
    public List<AdicionalEventoResponseDTO> listar(UUID eventoId) {
        eventosClient.obtener(eventoId);
        return adicionalRepository.findAllByEventoIdOrderByCreadoEnAsc(eventoId).stream()
                .map(AdicionalEventoResponseDTO::from).toList();
    }

    /**
     * Agrega el adicional y publica SERVICIO_ADICIONAL_AGREGADO DESPUÉS del commit (clave = eventoId). Si Kafka no está
     * disponible, el adicional queda registrado igualmente (201) y el fallo se registra en el log.
     */
    @Transactional
    public AdicionalEventoResponseDTO agregar(UUID eventoId, AdicionalRequest request) {
        if (!cotizacionRepository.existsByEventoId(eventoId)) {
            throw new RecursoNoEncontradoException("No existe una cotización para el evento " + eventoId
                    + ". Genera la cotización antes de agregar servicios adicionales.");
        }
        AdicionalCatalogoResponse item = catalog.get(request.codigo());
        if (item == null) throw new SolicitudInvalidaException("codigo", "no existe en el catálogo de servicios adicionales");
        BigDecimal subtotal = item.costoUnitario().multiply(BigDecimal.valueOf(request.cantidad()));
        AdicionalEvento saved = adicionalRepository.saveAndFlush(new AdicionalEvento(
                eventoId, item.codigo(), item.nombre(), request.cantidad(), item.costoUnitario(), subtotal));
        publicador.publishEvent(MensajeInterno.servicioAdicionalAgregado(saved, item.moneda()));
        LOGGER.info("Servicio adicional agregado. adicionalId={}, eventoId={}, codigo={}", saved.getId(), eventoId,
                saved.getCodigo());
        return AdicionalEventoResponseDTO.from(saved);
    }
}

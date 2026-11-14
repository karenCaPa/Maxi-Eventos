package com.maxieventos.finanzas.service;

import com.maxieventos.finanzas.dto.AdicionalCatalogoResponse;
import com.maxieventos.finanzas.dto.BalanceResponse;
import com.maxieventos.finanzas.dto.CotizacionResponseDTO;
import com.maxieventos.finanzas.dto.ResultadoCreacion;
import com.maxieventos.finanzas.dto.remoto.EventoRemoto;
import com.maxieventos.finanzas.entity.Cotizacion;
import com.maxieventos.finanzas.entity.DesgloseCotizacion;
import com.maxieventos.finanzas.exception.RecursoNoEncontradoException;
import com.maxieventos.finanzas.exception.ReglaNegocioException;
import com.maxieventos.finanzas.repository.AdicionalEventoRepository;
import com.maxieventos.finanzas.repository.CotizacionRepository;
import com.maxieventos.finanzas.repository.PagoRepository;
import com.maxieventos.finanzas.service.remoto.EventosClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CotizacionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CotizacionService.class);

    private final CotizacionRepository cotizacionRepository;
    private final AdicionalEventoRepository adicionalRepository;
    private final PagoRepository pagoRepository;
    private final EventosClient eventosClient;
    private final CalculadoraCotizacion calculadora;
    private final String moneda;

    public CotizacionService(CotizacionRepository cotizacionRepository,
                             AdicionalEventoRepository adicionalRepository,
                             PagoRepository pagoRepository,
                             EventosClient eventosClient,
                             CalculadoraCotizacion calculadora,
                             @Value("${finanzas.moneda}") String moneda) {
        this.cotizacionRepository = cotizacionRepository;
        this.adicionalRepository = adicionalRepository;
        this.pagoRepository = pagoRepository;
        this.eventosClient = eventosClient;
        this.calculadora = calculadora;
        this.moneda = moneda;
    }

    /**
     * Cotiza el evento con los datos que devuelve eventos-service (tipo, zona, adultos y niños) y las tarifas de la
     * configuración. La cotización no se regenera (decisión de la Fase 4: Eventos no permite cambiar esos datos): si el
     * evento ya tiene cotización se devuelve la existente ({@code creado = false}) sin consultar a Eventos.
     * Evento inexistente → 404; no PENDIENTE → 409; Eventos caído → 503.
     */
    public ResultadoCreacion<CotizacionResponseDTO> cotizar(UUID eventoId) {
        Optional<Cotizacion> existente = cotizacionRepository.findByEventoId(eventoId);
        if (existente.isPresent()) {
            return new ResultadoCreacion<>(aDto(existente.get()), false);
        }
        EventoRemoto evento = eventosClient.obtener(eventoId);
        if (!evento.esPendiente()) {
            throw new ReglaNegocioException("Solo se puede cotizar un evento PENDIENTE; el evento está " + evento.estado() + ".");
        }
        DesgloseCotizacion desglose = calculadora.calcular(evento.tipoEvento(), evento.zona(),
                evento.cantidadAdultos(), evento.cantidadNinos());
        try {
            Cotizacion cotizacion = cotizacionRepository.saveAndFlush(new Cotizacion(eventoId, desglose, moneda));
            LOGGER.info("Cotización creada. cotizacionId={}, eventoId={}, tipo={}, zona={}, adultos={}, ninos={}, total={}, "
                            + "montoReservaRequerido={}", cotizacion.getId(), eventoId, desglose.tipoEvento(), desglose.zona(),
                    desglose.cantidadAdultos(), desglose.cantidadNinos(), desglose.total(), desglose.montoReservaRequerido());
            return new ResultadoCreacion<>(aDto(cotizacion), true);
        } catch (DataIntegrityViolationException ex) {
            // Otra petición creó la cotización del mismo evento al mismo tiempo (restricción UNIQUE por evento).
            return cotizacionRepository.findByEventoId(eventoId)
                    .map(otra -> new ResultadoCreacion<>(aDto(otra), false))
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public CotizacionResponseDTO obtenerCotizacion(UUID eventoId) {
        return aDto(buscarCotizacion(eventoId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse obtenerBalance(UUID eventoId) {
        Cotizacion quote = buscarCotizacion(eventoId);
        BigDecimal adicionales = adicionalRepository.totalPorEvento(eventoId);
        BigDecimal abonado = pagoRepository.totalAbonado(eventoId);
        BigDecimal total = quote.getTotal().add(adicionales);
        return new BalanceResponse(eventoId, quote.getTotal(), adicionales, abonado,
                total.subtract(abonado).max(BigDecimal.ZERO), total, montoReserva(quote), quote.getMoneda());
    }

    public List<AdicionalCatalogoResponse> catalogo(BigDecimal decoracion, BigDecimal horaExtra, BigDecimal transporte) {
        return List.of(
                new AdicionalCatalogoResponse("decoracion-premium", "Decoración premium", decoracion, moneda),
                new AdicionalCatalogoResponse("hora-extra", "Hora adicional de servicio", horaExtra, moneda),
                new AdicionalCatalogoResponse("transporte-adicional", "Transporte adicional", transporte, moneda));
    }

    /**
     * Monto de reserva guardado en la cotización. Las cotizaciones creadas antes de la Fase 3 no lo tienen
     * (columna nula): se calcula con el porcentaje configurado, sin modificar la cotización guardada.
     */
    private BigDecimal montoReserva(Cotizacion cotizacion) {
        if (cotizacion.getMontoReservaRequerido() != null) {
            return cotizacion.getMontoReservaRequerido();
        }
        LOGGER.warn("Cotización sin monto de reserva guardado (anterior a la Fase 3). cotizacionId={}; se calcula con "
                + "el porcentaje configurado", cotizacion.getId());
        return calculadora.reserva(cotizacion.getTotal());
    }

    private CotizacionResponseDTO aDto(Cotizacion cotizacion) {
        return CotizacionResponseDTO.from(cotizacion, montoReserva(cotizacion));
    }

    private Cotizacion buscarCotizacion(UUID eventoId) {
        return cotizacionRepository.findByEventoId(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una cotización para el evento " + eventoId));
    }
}

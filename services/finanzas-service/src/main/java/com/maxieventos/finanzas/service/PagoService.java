package com.maxieventos.finanzas.service;

import com.maxieventos.finanzas.dto.PagoRequest;
import com.maxieventos.finanzas.dto.PagoResponse;
import com.maxieventos.finanzas.dto.PaginaResponse;
import com.maxieventos.finanzas.dto.remoto.EventoRemoto;
import com.maxieventos.finanzas.entity.Cotizacion;
import com.maxieventos.finanzas.entity.Pago;
import com.maxieventos.finanzas.exception.ReglaNegocioException;
import com.maxieventos.finanzas.exception.SolicitudInvalidaException;
import com.maxieventos.finanzas.kafka.MensajeInterno;
import com.maxieventos.finanzas.repository.AdicionalEventoRepository;
import com.maxieventos.finanzas.repository.CotizacionRepository;
import com.maxieventos.finanzas.repository.PagoRepository;
import com.maxieventos.finanzas.service.remoto.EventosClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Abonos de un evento.
 * <ul>
 *   <li>Validaciones: el evento existe (404) y no está CANCELADO (409); el cliente es el del evento y la moneda la de la
 *       cotización (400); hay cotización (409); el monto no supera el saldo pendiente (409).</li>
 *   <li>Idempotencia en PostgreSQL: columna {@code clave_idempotencia} con índice UNIQUE. Repetir la misma clave con
 *       los mismos datos devuelve el pago ya registrado; con otros datos → 409.</li>
 *   <li>La cotización del evento se bloquea ({@code FOR UPDATE}) mientras se calcula el saldo y se guarda el pago:
 *       dos abonos simultáneos no pueden superar juntos el saldo.</li>
 *   <li>PAGO_REGISTRADO se guarda en el outbox en la misma transacción y se publica DESPUÉS del commit, con clave =
 *       eventoId ({@link com.maxieventos.finanzas.kafka.OutboxKafka}).</li>
 * </ul>
 * La llamada REST a eventos-service se hace fuera de la transacción (no retiene la conexión ni el bloqueo).
 */
@Service
public class PagoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagoRepository;
    private final CotizacionRepository cotizacionRepository;
    private final AdicionalEventoRepository adicionalRepository;
    private final EventosClient eventosClient;
    private final TransactionTemplate transaccion;
    private final ApplicationEventPublisher publicador;

    public PagoService(PagoRepository pagoRepository, CotizacionRepository cotizacionRepository,
                       AdicionalEventoRepository adicionalRepository, EventosClient eventosClient,
                       TransactionTemplate transaccion, ApplicationEventPublisher publicador) {
        this.pagoRepository = pagoRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.adicionalRepository = adicionalRepository;
        this.eventosClient = eventosClient;
        this.transaccion = transaccion;
        this.publicador = publicador;
    }

    public RegistroPago registrarPago(PagoRequest request, String clave) {
        var previo = pagoRepository.findByClaveIdempotencia(clave);
        if (previo.isPresent()) {
            return repeticion(previo.get(), request);
        }
        EventoRemoto evento = eventosClient.obtener(request.eventoId());
        if (evento.esCancelado()) {
            throw new ReglaNegocioException("El evento está CANCELADO y no admite abonos.");
        }
        if (!request.clienteId().equals(evento.clienteId())) {
            throw new SolicitudInvalidaException("clienteId", "no corresponde al cliente del evento");
        }
        try {
            Resultado resultado = transaccion.execute(estado -> registrarEnTransaccion(request, clave));
            return resultado.repetido() ? repeticion(resultado.pago(), request)
                    : new RegistroPago(PagoResponse.from(resultado.pago()), false);
        } catch (DataIntegrityViolationException ex) {
            // Otra petición con la misma clave se registró al mismo tiempo: la restricción UNIQUE la detectó.
            Pago otro = pagoRepository.findByClaveIdempotencia(clave).orElseThrow(() -> ex);
            return repeticion(otro, request);
        }
    }

    public PaginaResponse<PagoResponse> listar(UUID eventoId, Pageable pageable) {
        eventosClient.obtener(eventoId);
        return PaginaResponse.de(pagoRepository.findByEventoId(eventoId, pageable), PagoResponse::from);
    }

    private Resultado registrarEnTransaccion(PagoRequest request, String clave) {
        UUID eventoId = request.eventoId();
        Cotizacion cotizacion = cotizacionRepository.bloquearPorEventoId(eventoId)
                .orElseThrow(() -> new ReglaNegocioException(
                        "El evento no tiene cotización. Genera la cotización antes de registrar abonos."));
        // Con el bloqueo tomado: si otra petición con la misma clave ya se registró, se devuelve esa.
        var yaRegistrado = pagoRepository.findByClaveIdempotencia(clave);
        if (yaRegistrado.isPresent()) {
            return new Resultado(yaRegistrado.get(), true);
        }
        if (!cotizacion.getMoneda().equalsIgnoreCase(request.moneda())) {
            throw new SolicitudInvalidaException("moneda", "debe ser " + cotizacion.getMoneda() + ", la moneda de la cotización");
        }
        BigDecimal saldo = cotizacion.getTotal().add(adicionalRepository.totalPorEvento(eventoId))
                .subtract(pagoRepository.totalAbonado(eventoId));
        if (saldo.signum() <= 0) {
            throw new ReglaNegocioException("El evento ya está pagado por completo; no admite más abonos.");
        }
        if (request.monto().compareTo(saldo) > 0) {
            throw new ReglaNegocioException("El monto " + request.monto().toPlainString() + " supera el saldo pendiente de "
                    + saldo.toPlainString() + " " + cotizacion.getMoneda() + ".");
        }
        Pago pago = pagoRepository.saveAndFlush(new Pago(eventoId, request.clienteId(), request.metodoPago(),
                request.monto(), cotizacion.getMoneda(), clave));
        publicador.publishEvent(MensajeInterno.pagoRegistrado(pago));
        LOGGER.info("Pago registrado. pagoId={}, eventoId={}, monto={} {}, saldoAnterior={}", pago.getId(), eventoId,
                pago.getMonto(), pago.getMoneda(), saldo);
        return new Resultado(pago, false);
    }

    /** Misma clave: si los datos coinciden es una repetición (se devuelve el pago ya registrado); si no → 409. */
    private static RegistroPago repeticion(Pago previo, PagoRequest request) {
        boolean mismosDatos = previo.getEventoId().equals(request.eventoId())
                && previo.getClienteId().equals(request.clienteId())
                && previo.getMonto().compareTo(request.monto()) == 0;
        if (!mismosDatos) {
            throw new ReglaNegocioException("La clave de idempotencia " + previo.getClaveIdempotencia()
                    + " ya se usó para otro pago con datos distintos.");
        }
        LOGGER.info("Pago repetido con la misma clave de idempotencia: se devuelve el ya registrado. pagoId={}, eventoId={}",
                previo.getId(), previo.getEventoId());
        return new RegistroPago(PagoResponse.from(previo), true);
    }

    private record Resultado(Pago pago, boolean repetido) {
    }

    public record RegistroPago(PagoResponse pago, boolean duplicado) {
    }
}

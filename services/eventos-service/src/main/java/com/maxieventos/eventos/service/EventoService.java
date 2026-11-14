package com.maxieventos.eventos.service;

import com.maxieventos.eventos.dto.CrearEventoRequest;
import com.maxieventos.eventos.dto.EventoResponse;
import com.maxieventos.eventos.dto.PaginaResponse;
import com.maxieventos.eventos.dto.remoto.BalanceRemoto;
import com.maxieventos.eventos.dto.remoto.ContratoRemoto;
import com.maxieventos.eventos.dto.remoto.LugarRemoto;
import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.FechaAgenda;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.exception.RecursoNoEncontradoException;
import com.maxieventos.eventos.exception.ReglaNegocioException;
import com.maxieventos.eventos.exception.SolicitudInvalidaException;
import com.maxieventos.eventos.kafka.EventoConfirmadoInterno;
import com.maxieventos.eventos.repository.EventoFiltros;
import com.maxieventos.eventos.repository.EventoRepository;
import com.maxieventos.eventos.repository.FechaAgendaRepository;
import com.maxieventos.eventos.service.remoto.ClientesClient;
import com.maxieventos.eventos.service.remoto.ContratosClient;
import com.maxieventos.eventos.service.remoto.FinanzasClient;
import com.maxieventos.eventos.service.remoto.LugaresClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reglas de negocio de los eventos. Las validaciones contra otros servicios (REST) se hacen FUERA de la transacción
 * para no retener una conexión de base de datos mientras se espera la red; los cambios en BD van en una sola
 * transacción ({@link TransactionTemplate}) que vuelve a comprobar el estado.
 */
@Service
public class EventoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventoService.class);

    private final EventoRepository eventoRepository;
    private final FechaAgendaRepository fechaRepository;
    private final ClientesClient clientesClient;
    private final LugaresClient lugaresClient;
    private final ContratosClient contratosClient;
    private final FinanzasClient finanzasClient;
    private final TransactionTemplate transaccion;
    private final ApplicationEventPublisher publicador;
    private final Clock reloj;
    private final DiasPermitidos diasPermitidos;

    public EventoService(EventoRepository eventoRepository, FechaAgendaRepository fechaRepository,
                         ClientesClient clientesClient, LugaresClient lugaresClient, ContratosClient contratosClient,
                         FinanzasClient finanzasClient, TransactionTemplate transaccion,
                         ApplicationEventPublisher publicador, Clock reloj, DiasPermitidos diasPermitidos) {
        this.eventoRepository = eventoRepository;
        this.fechaRepository = fechaRepository;
        this.clientesClient = clientesClient;
        this.lugaresClient = lugaresClient;
        this.contratosClient = contratosClient;
        this.finanzasClient = finanzasClient;
        this.transaccion = transaccion;
        this.publicador = publicador;
        this.reloj = reloj;
        this.diasPermitidos = diasPermitidos;
    }

    // ------------------------------------------------------------------ crear

    public EventoResponse crear(CrearEventoRequest request) {
        if (!request.fecha().isAfter(LocalDate.now(reloj))) {
            throw new SolicitudInvalidaException("fecha", "debe ser una fecha posterior a hoy");
        }
        if (!diasPermitidos.permite(request.fecha())) {
            throw new SolicitudInvalidaException("fecha", diasPermitidos.mensaje());
        }
        int invitados = request.cantidadAdultos() + request.cantidadNinos();
        if (invitados < 1) {
            throw new SolicitudInvalidaException("cantidadAdultos", "el evento debe tener al menos un invitado");
        }
        if (!clientesClient.existe(request.clienteId())) {
            throw new RecursoNoEncontradoException("No existe un cliente con id " + request.clienteId());
        }
        if (request.lugarId() != null) {
            validarLugar(request.lugarId(), invitados);
        }
        Evento evento = transaccion.execute(estado -> {
            FechaAgenda agenda = validarFechaParaEvento(request.fecha());
            Evento nuevo = eventoRepository.save(new Evento(request.nombreEvento().trim(), request.fecha(),
                    request.clienteId(), request.tipoEvento(), request.cantidadAdultos(), request.cantidadNinos(),
                    request.zona(), request.lugarId()));
            agenda.asignar(EstadoFecha.PENDIENTE, nuevo.getId());
            fechaRepository.save(agenda);
            return nuevo;
        });
        LOGGER.info("Evento creado. eventoId={}, fecha={}, tipo={}, invitados={}", evento.getId(), evento.getFecha(),
                evento.getTipoEvento(), evento.getNumInvitados());
        return EventoResponse.from(evento);
    }

    // ------------------------------------------------------------------ consultar

    public EventoResponse obtener(UUID id) {
        return EventoResponse.from(buscar(id));
    }

    public PaginaResponse<EventoResponse> listar(EstadoEvento estado, TipoEvento tipo, LocalDate desde, LocalDate hasta,
                                                 Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("desde", "debe ser anterior o igual a hasta");
        }
        return PaginaResponse.de(eventoRepository.findAll(EventoFiltros.de(estado, tipo, desde, hasta), pageable),
                EventoResponse::from);
    }

    // ------------------------------------------------------------------ confirmar

    /**
     * Confirma el evento: su fecha no puede haber pasado (Fase 11I), el contrato debe estar FIRMADO (Contratos) y lo
     * abonado debe cubrir el monto de reserva (Finanzas). En una transacción el evento pasa a RESERVADO y la fecha a OCUPADA; EVENTO_CONFIRMADO se publica
     * después del commit.
     */
    public EventoResponse confirmar(UUID id) {
        validarConfirmable(buscar(id));

        ContratoRemoto contrato = contratosClient.obtenerDeEvento(id).orElseThrow(() -> new ReglaNegocioException(
                "El evento no tiene contrato. Genera el contrato y márcalo como firmado antes de confirmar."));
        if (!contrato.firmado()) {
            throw new ReglaNegocioException("El contrato del evento está en estado " + contrato.estado()
                    + "; debe estar FIRMADO para confirmar el evento.");
        }
        BalanceRemoto balance = finanzasClient.obtenerBalance(id).orElseThrow(() -> new ReglaNegocioException(
                "El evento no tiene cotización. Genera la cotización y registra el abono de la reserva antes de confirmar."));
        validarReservaAbonada(balance);

        Evento confirmado = transaccion.execute(estado -> {
            Evento evento = buscar(id);
            validarConfirmable(evento);
            evento.confirmar();
            FechaAgenda agenda = fechaRepository.findById(evento.getFecha())
                    .orElseGet(() -> new FechaAgenda(evento.getFecha()));
            agenda.asignar(EstadoFecha.OCUPADA, evento.getId());
            fechaRepository.save(agenda);
            Evento guardado = eventoRepository.save(evento);
            publicador.publishEvent(EventoConfirmadoInterno.de(guardado));
            return guardado;
        });
        LOGGER.info("Evento confirmado. eventoId={}, fecha={} (EVENTO_CONFIRMADO se publica después del commit)",
                confirmado.getId(), confirmado.getFecha());
        return EventoResponse.from(confirmado);
    }

    // ------------------------------------------------------------------ cancelar

    public EventoResponse cancelar(UUID id) {
        Evento cancelado = transaccion.execute(estado -> {
            Evento evento = buscar(id);
            if (evento.getEstado() == EstadoEvento.CANCELADO) {
                throw new ReglaNegocioException("El evento ya está cancelado.");
            }
            evento.cancelar();
            fechaRepository.findById(evento.getFecha())
                    .filter(agenda -> id.equals(agenda.getEventoId()))
                    .ifPresent(agenda -> {
                        agenda.liberar();
                        fechaRepository.save(agenda);
                    });
            return eventoRepository.save(evento);
        });
        LOGGER.info("Evento cancelado. eventoId={}, fecha liberada={}", cancelado.getId(), cancelado.getFecha());
        return EventoResponse.from(cancelado);
    }

    // ------------------------------------------------------------------ asignar lugar

    public EventoResponse asignarLugar(UUID id, UUID lugarId) {
        Evento evento = buscar(id);
        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ReglaNegocioException("No se puede asignar un lugar a un evento cancelado.");
        }
        validarLugar(lugarId, evento.getNumInvitados());
        Evento actualizado = transaccion.execute(estado -> {
            Evento actual = buscar(id);
            if (actual.getEstado() == EstadoEvento.CANCELADO) {
                throw new ReglaNegocioException("No se puede asignar un lugar a un evento cancelado.");
            }
            actual.asignarLugar(lugarId);
            return eventoRepository.save(actual);
        });
        LOGGER.info("Lugar asignado al evento. eventoId={}, lugarId={}", id, lugarId);
        return EventoResponse.from(actualizado);
    }

    // ------------------------------------------------------------------ reglas

    /**
     * La fecha debe estar libre y no debe haber otro evento (o bloqueo) en el mismo fin de semana.
     * Devuelve el registro de agenda (existente o nuevo) que se va a ocupar.
     */
    FechaAgenda validarFechaParaEvento(LocalDate fecha) {
        List<LocalDate> fechas = new ArrayList<>(ReglaFinDeSemana.otrosDiasDelFinDeSemana(fecha));
        fechas.add(fecha);
        List<FechaAgenda> registros = fechaRepository.findAllByFechaIn(fechas);
        FechaAgenda agenda = registros.stream().filter(r -> r.getFecha().equals(fecha)).findFirst()
                .orElseGet(() -> new FechaAgenda(fecha));
        if (!agenda.estaLibre()) {
            throw new ReglaNegocioException("La fecha " + fecha + " no está disponible (estado " + agenda.getEstado()
                    + "). Elige otra fecha.");
        }
        ReglaFinDeSemana.conflicto(fecha, registros).ifPresent(ocupada -> {
            throw new ReglaNegocioException("Ya hay un evento o bloqueo el " + ocupada.getFecha() + " ("
                    + ocupada.getEstado() + "), en el mismo fin de semana. Solo se permite un evento por fin de semana "
                    + "(viernes a domingo).");
        });
        return agenda;
    }

    /** Fase 11I: mensaje del 409 al confirmar un evento cuya fecha ya pasó. */
    static final String MENSAJE_FECHA_PASADA = "No se puede confirmar un evento cuya fecha ya pasó.";

    private void validarConfirmable(Evento evento) {
        if (evento.getEstado() == EstadoEvento.RESERVADO) {
            throw new ReglaNegocioException("El evento ya está confirmado (RESERVADO).");
        }
        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ReglaNegocioException("El evento está cancelado y no se puede confirmar.");
        }
        // Fase 11I: "hoy" en la zona del negocio (Clock inyectado). Un evento de hoy todavía se puede confirmar.
        if (evento.getFecha().isBefore(LocalDate.now(reloj))) {
            throw new ReglaNegocioException(MENSAJE_FECHA_PASADA);
        }
    }

    private static void validarReservaAbonada(BalanceRemoto balance) {
        BigDecimal reserva = balance.montoReservaRequerido();
        if (reserva == null) {
            throw new ReglaNegocioException("La cotización del evento no tiene monto de reserva requerido.");
        }
        BigDecimal abonado = balance.totalAbonado() == null ? BigDecimal.ZERO : balance.totalAbonado();
        if (abonado.compareTo(reserva) < 0) {
            String moneda = balance.moneda() == null ? "" : " " + balance.moneda();
            throw new ReglaNegocioException("Lo abonado (" + abonado.toPlainString() + moneda
                    + ") no alcanza el monto de reserva requerido (" + reserva.toPlainString() + moneda + "). Faltan "
                    + reserva.subtract(abonado).toPlainString() + moneda + ".");
        }
    }

    private void validarLugar(UUID lugarId, int invitados) {
        LugarRemoto lugar = lugaresClient.obtener(lugarId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un lugar con id " + lugarId));
        // estado y capacidad solo se validan si Lugares los envía (estado se agrega en la Fase 4)
        if (lugar.estado() != null && !"ACTIVO".equalsIgnoreCase(lugar.estado())) {
            throw new ReglaNegocioException("El lugar " + lugar.nombre() + " está " + lugar.estado()
                    + " y no se puede asignar a un evento.");
        }
        if (lugar.capacidad() != null && lugar.capacidad() < invitados) {
            throw new ReglaNegocioException("El lugar " + lugar.nombre() + " tiene capacidad para " + lugar.capacidad()
                    + " personas y el evento tiene " + invitados + " invitados.");
        }
    }

    private Evento buscar(UUID id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un evento con id " + id));
    }
}

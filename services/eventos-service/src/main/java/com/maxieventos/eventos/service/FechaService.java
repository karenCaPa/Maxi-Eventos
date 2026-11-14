package com.maxieventos.eventos.service;

import com.maxieventos.eventos.dto.FechaResponse;
import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.FechaAgenda;
import com.maxieventos.eventos.exception.ReglaNegocioException;
import com.maxieventos.eventos.exception.SolicitudInvalidaException;
import com.maxieventos.eventos.repository.FechaAgendaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agenda de fechas: disponibilidad (la usa también el gateway GraphQL) y bloqueos manuales. */
@Service
public class FechaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FechaService.class);
    static final int DIAS_POR_DEFECTO = 90;
    static final int DIAS_MAXIMO = 366;

    private final FechaAgendaRepository fechaRepository;
    private final Clock reloj;
    private final DiasPermitidos diasPermitidos;

    public FechaService(FechaAgendaRepository fechaRepository, Clock reloj, DiasPermitidos diasPermitidos) {
        this.fechaRepository = fechaRepository;
        this.reloj = reloj;
        this.diasPermitidos = diasPermitidos;
    }

    /**
     * Fechas del rango en las que se puede crear un evento: días permitidos ({@link DiasPermitidos}), libres (sin
     * registro o DISPONIBLE) y no bloqueadas por la regla del fin de semana. Por defecto desde mañana y por 90 días; como máximo 366 días.
     */
    @Transactional(readOnly = true)
    public List<FechaResponse> disponibles(LocalDate desde, LocalDate hasta) {
        LocalDate manana = LocalDate.now(reloj).plusDays(1);
        LocalDate inicio = desde == null ? manana : desde;
        LocalDate fin = hasta == null ? inicio.plusDays(DIAS_POR_DEFECTO - 1L) : hasta;
        validarRango(inicio, fin);
        if (inicio.isBefore(manana)) {
            inicio = manana;
        }
        // Se cargan 2 días antes y después para evaluar fines de semana que empiezan o terminan fuera del rango
        List<FechaAgenda> registros = fechaRepository.findAllByFechaBetweenOrderByFechaAsc(inicio.minusDays(2), fin.plusDays(2));
        Map<LocalDate, FechaAgenda> porFecha = registros.stream()
                .collect(Collectors.toMap(FechaAgenda::getFecha, Function.identity()));
        List<FechaResponse> resultado = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
            if (!diasPermitidos.permite(dia)) {
                continue;
            }
            FechaAgenda registro = porFecha.get(dia);
            boolean libre = registro == null || registro.estaLibre();
            if (libre && ReglaFinDeSemana.conflicto(dia, registros).isEmpty()) {
                resultado.add(FechaResponse.disponible(dia));
            }
        }
        return resultado;
    }

    /** Fechas registradas en la agenda (no disponibles o con historial) dentro del rango. */
    @Transactional(readOnly = true)
    public List<FechaResponse> agenda(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde == null ? LocalDate.now(reloj) : desde;
        LocalDate fin = hasta == null ? inicio.plusDays(DIAS_POR_DEFECTO - 1L) : hasta;
        validarRango(inicio, fin);
        return fechaRepository.findAllByFechaBetweenOrderByFechaAsc(inicio, fin).stream().map(FechaResponse::from).toList();
    }

    /**
     * Cambio manual del estado de una fecha (bloqueo o liberación):
     * con un evento activo no se cambia (409, se debe cancelar o confirmar el evento); un bloqueo (PENDIENTE u
     * OCUPADA sin evento) solo se permite en los días permitidos (400 en otro día) y respeta la regla del fin de semana;
     * DISPONIBLE (liberar) se permite en cualquier día si la fecha no tiene evento, para poder limpiar registros antiguos.
     */
    @Transactional
    public FechaResponse actualizarEstado(LocalDate fecha, EstadoFecha estado) {
        if (estado != EstadoFecha.DISPONIBLE && !diasPermitidos.permite(fecha)) {
            throw new SolicitudInvalidaException("fecha", diasPermitidos.mensaje());
        }
        List<LocalDate> fechas = new ArrayList<>(ReglaFinDeSemana.otrosDiasDelFinDeSemana(fecha));
        fechas.add(fecha);
        List<FechaAgenda> registros = fechaRepository.findAllByFechaIn(fechas);
        FechaAgenda agenda = registros.stream().filter(r -> r.getFecha().equals(fecha)).findFirst()
                .orElseGet(() -> new FechaAgenda(fecha));
        if (agenda.getEventoId() != null) {
            throw new ReglaNegocioException("La fecha " + fecha + " tiene el evento " + agenda.getEventoId() + " ("
                    + agenda.getEstado() + "). Para cambiarla, cancela o confirma el evento.");
        }
        if (estado == EstadoFecha.DISPONIBLE) {
            agenda.liberar();
        } else {
            ReglaFinDeSemana.conflicto(fecha, registros).ifPresent(ocupada -> {
                throw new ReglaNegocioException("No se puede bloquear la fecha " + fecha + ": el " + ocupada.getFecha()
                        + " del mismo fin de semana ya está " + ocupada.getEstado() + ".");
            });
            agenda.asignar(estado, null);
        }
        FechaAgenda guardada = fechaRepository.save(agenda);
        LOGGER.info("Estado de fecha actualizado manualmente. fecha={}, estado={}", fecha, estado);
        return FechaResponse.from(guardada);
    }

    private static void validarRango(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) {
            throw new SolicitudInvalidaException("hasta", "debe ser posterior o igual a desde");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_MAXIMO) {
            throw new SolicitudInvalidaException("hasta", "el rango no puede superar " + DIAS_MAXIMO + " días");
        }
    }
}

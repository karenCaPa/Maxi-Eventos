package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.eventos.dto.FechaResponse;
import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.FechaAgenda;
import com.maxieventos.eventos.exception.ReglaNegocioException;
import com.maxieventos.eventos.exception.SolicitudInvalidaException;
import com.maxieventos.eventos.repository.FechaAgendaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Agenda de fechas: disponibilidad y bloqueos manuales")
class FechaServiceTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 12, 4);
    private static final LocalDate SABADO = VIERNES.plusDays(1);
    private static final LocalDate DOMINGO = VIERNES.plusDays(2);

    private FechaAgendaRepository repositorio;
    private FechaService service;

    @BeforeEach
    void preparar() {
        repositorio = mock(FechaAgendaRepository.class);
        service = servicio(DiasPermitidos.FIN_DE_SEMANA);
        when(repositorio.save(any(FechaAgenda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** "Hoy" = martes 27 de octubre de 2026 en Neiva. */
    private FechaService servicio(DiasPermitidos dias) {
        return new FechaService(repositorio, Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Bogota")),
                dias);
    }

    private static FechaAgenda registro(LocalDate fecha, EstadoFecha estado, UUID eventoId) {
        FechaAgenda agenda = new FechaAgenda(fecha);
        agenda.asignar(estado, eventoId);
        return agenda;
    }

    @Test
    void unEventoElSabadoBloqueaTodoElFinDeSemana() {
        when(repositorio.findAllByFechaBetweenOrderByFechaAsc(any(), any()))
                .thenReturn(List.of(registro(SABADO, EstadoFecha.PENDIENTE, UUID.randomUUID())));

        List<LocalDate> disponibles = service.disponibles(VIERNES.minusDays(1), DOMINGO.plusWeeks(1)).stream()
                .map(FechaResponse::fecha).toList();

        // Ese fin de semana desaparece entero y los días entre semana no se ofrecen: solo queda el fin de semana siguiente
        assertThat(disponibles).containsExactly(VIERNES.plusWeeks(1), SABADO.plusWeeks(1), DOMINGO.plusWeeks(1));
    }

    @Test
    void porDefectoDesdeMananaYNoventaDiasSoloLosDiasPermitidos() {
        when(repositorio.findAllByFechaBetweenOrderByFechaAsc(any(), any())).thenReturn(List.of());
        List<FechaResponse> disponibles = service.disponibles(null, null);
        // Del miércoles 28-oct-2026 al lunes 25-ene-2027 (90 días) hay 39 viernes, sábados y domingos
        assertThat(disponibles).hasSize(39);
        assertThat(disponibles.get(0).fecha()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(disponibles.get(0).diaSemana()).isEqualTo("viernes");
        assertThat(disponibles).extracting(FechaResponse::diaSemana).containsOnly("viernes", "sábado", "domingo");
    }

    @Test
    void lasFechasDisponiblesUsanLosDiasConfigurados() {
        when(repositorio.findAllByFechaBetweenOrderByFechaAsc(any(), any())).thenReturn(List.of());
        List<LocalDate> soloSabados = servicio(DiasPermitidos.desde("SATURDAY")).disponibles(VIERNES, DOMINGO.plusWeeks(1))
                .stream().map(FechaResponse::fecha).toList();
        assertThat(soloSabados).containsExactly(SABADO, SABADO.plusWeeks(1));
    }

    @Test
    void unRangoMayorA366DiasEsInvalido() {
        assertThatThrownBy(() -> service.disponibles(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 3)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void hastaAntesDeDesdeEsInvalido() {
        assertThatThrownBy(() -> service.disponibles(SABADO, VIERNES)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void unaFechaConEventoNoSeCambiaManualmente() {
        when(repositorio.findAllByFechaIn(anyCollection()))
                .thenReturn(List.of(registro(SABADO, EstadoFecha.PENDIENTE, UUID.randomUUID())));
        assertThatThrownBy(() -> service.actualizarEstado(SABADO, EstadoFecha.DISPONIBLE))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cancela o confirma el evento");
    }

    @Test
    void unBloqueoManualRespetaLaReglaDelFinDeSemana() {
        when(repositorio.findAllByFechaIn(anyCollection()))
                .thenReturn(List.of(registro(VIERNES, EstadoFecha.OCUPADA, UUID.randomUUID())));
        assertThatThrownBy(() -> service.actualizarEstado(DOMINGO, EstadoFecha.OCUPADA))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mismo fin de semana");
    }

    @Test
    void sePuedeBloquearYLiberarUnaFechaSinEvento() {
        when(repositorio.findAllByFechaIn(anyCollection())).thenReturn(List.of());
        assertThat(service.actualizarEstado(SABADO, EstadoFecha.OCUPADA).estado()).isEqualTo(EstadoFecha.OCUPADA);

        when(repositorio.findAllByFechaIn(anyCollection())).thenReturn(List.of(registro(SABADO, EstadoFecha.OCUPADA, null)));
        assertThat(service.actualizarEstado(SABADO, EstadoFecha.DISPONIBLE).estado()).isEqualTo(EstadoFecha.DISPONIBLE);
    }

    @Test
    void bloquearUnDiaNoPermitidoDa400EnElCampoFecha() {
        LocalDate martes = VIERNES.plusDays(4);
        for (EstadoFecha bloqueo : List.of(EstadoFecha.PENDIENTE, EstadoFecha.OCUPADA)) {
            assertThatThrownBy(() -> service.actualizarEstado(martes, bloqueo))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessage("Solo se pueden registrar eventos en viernes, sábado o domingo.")
                    .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCampo()).isEqualTo("fecha"));
        }
        verify(repositorio, never()).save(any());
    }

    @Test
    void liberarSePermiteEnCualquierDia() {
        LocalDate martes = VIERNES.plusDays(4);
        when(repositorio.findAllByFechaIn(anyCollection())).thenReturn(List.of(registro(martes, EstadoFecha.OCUPADA, null)));
        assertThat(service.actualizarEstado(martes, EstadoFecha.DISPONIBLE).estado()).isEqualTo(EstadoFecha.DISPONIBLE);
    }
}

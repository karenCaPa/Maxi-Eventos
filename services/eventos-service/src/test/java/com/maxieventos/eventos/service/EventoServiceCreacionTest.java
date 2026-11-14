package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.eventos.dto.CrearEventoRequest;
import com.maxieventos.eventos.dto.EventoResponse;
import com.maxieventos.eventos.dto.remoto.LugarRemoto;
import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.FechaAgenda;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.entity.ZonaEvento;
import com.maxieventos.eventos.exception.RecursoNoEncontradoException;
import com.maxieventos.eventos.exception.ReglaNegocioException;
import com.maxieventos.eventos.exception.SolicitudInvalidaException;
import com.maxieventos.eventos.repository.EventoRepository;
import com.maxieventos.eventos.repository.FechaAgendaRepository;
import com.maxieventos.eventos.service.remoto.ClientesClient;
import com.maxieventos.eventos.service.remoto.ContratosClient;
import com.maxieventos.eventos.service.remoto.FinanzasClient;
import com.maxieventos.eventos.service.remoto.LugaresClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Creación de un evento")
class EventoServiceCreacionTest {

    /** "Hoy" fijo para las pruebas: martes 27 de octubre de 2026 en Neiva. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 27);
    private static final LocalDate SABADO = LocalDate.of(2026, 12, 5);
    private static final LocalDate VIERNES = SABADO.minusDays(1);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 12, 9);

    private EventoRepository eventoRepository;
    private FechaAgendaRepository fechaRepository;
    private ClientesClient clientesClient;
    private LugaresClient lugaresClient;
    private EventoService service;
    private final UUID clienteId = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        eventoRepository = mock(EventoRepository.class);
        fechaRepository = mock(FechaAgendaRepository.class);
        clientesClient = mock(ClientesClient.class);
        lugaresClient = mock(LugaresClient.class);
        service = servicio(DiasPermitidos.FIN_DE_SEMANA);
        when(clientesClient.existe(clienteId)).thenReturn(true);
        when(fechaRepository.findAllByFechaIn(anyCollection())).thenReturn(List.of());
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> {
            Evento evento = inv.getArgument(0);
            ReflectionTestUtils.setField(evento, "id", UUID.randomUUID());
            return evento;
        });
        when(fechaRepository.save(any(FechaAgenda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EventoService servicio(DiasPermitidos dias) {
        // Reloj fijo a las 23:30 en Neiva (04:30 UTC del día siguiente): "hoy" sigue siendo el 27
        Clock reloj = Clock.fixed(Instant.parse("2026-10-28T04:30:00Z"), ZoneId.of("America/Bogota"));
        return new EventoService(eventoRepository, fechaRepository, clientesClient, lugaresClient,
                mock(ContratosClient.class), mock(FinanzasClient.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mock(ApplicationEventPublisher.class), reloj,
                dias);
    }

    private CrearEventoRequest solicitud(LocalDate fecha, UUID lugarId) {
        return new CrearEventoRequest("Quince de Sofía", clienteId, fecha, TipoEvento.QUINCE_ANOS, 80, 20, ZonaEvento.NEIVA, lugarId);
    }

    private static FechaAgenda registro(LocalDate fecha, EstadoFecha estado) {
        FechaAgenda agenda = new FechaAgenda(fecha);
        agenda.asignar(estado, UUID.randomUUID());
        return agenda;
    }

    @Test
    void creaElEventoPendienteYLaFechaPendiente() {
        EventoResponse creado = service.crear(solicitud(SABADO, null));

        assertThat(creado.estado()).isEqualTo(EstadoEvento.PENDIENTE);
        assertThat(creado.numInvitados()).isEqualTo(100);
        ArgumentCaptor<FechaAgenda> fecha = ArgumentCaptor.forClass(FechaAgenda.class);
        verify(fechaRepository).save(fecha.capture());
        assertThat(fecha.getValue().getFecha()).isEqualTo(SABADO);
        assertThat(fecha.getValue().getEstado()).isEqualTo(EstadoFecha.PENDIENTE);
        assertThat(fecha.getValue().getEventoId()).isEqualTo(creado.id());
    }

    @Test
    void laFechaDeHoyEsInvalida() {
        assertThatThrownBy(() -> service.crear(solicitud(HOY, null)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCampo()).isEqualTo("fecha"));
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void unaFechaPasadaEsInvalida() {
        assertThatThrownBy(() -> service.crear(solicitud(HOY.minusDays(1), null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void mananaEsValidaAunqueEnUtcYaSeaOtroDia() {
        // Mañana es miércoles: se usa una configuración que lo permite para probar solo el cálculo de "hoy"
        EventoService todosLosDias = servicio(DiasPermitidos.desde("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"));
        assertThat(todosLosDias.crear(solicitud(HOY.plusDays(1), null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
    }

    @Test
    void unDiaNoPermitidoDa400EnElCampoFechaSinConsultarNada() {
        assertThatThrownBy(() -> service.crear(solicitud(MIERCOLES, null)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Solo se pueden registrar eventos en viernes, sábado o domingo.")
                .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCampo()).isEqualTo("fecha"));
        verify(clientesClient, never()).existe(any());
        verify(eventoRepository, never()).save(any());
        verify(fechaRepository, never()).save(any());
    }

    @Test
    void viernesSabadoYDomingoSonPermitidos() {
        assertThat(service.crear(solicitud(VIERNES, null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
        assertThat(service.crear(solicitud(SABADO.plusWeeks(1), null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
        assertThat(service.crear(solicitud(SABADO.plusWeeks(2).plusDays(1), null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
    }

    @Test
    void losDiasPermitidosSalenDeLaConfiguracion() {
        EventoService soloSabados = servicio(DiasPermitidos.desde("saturday"));
        assertThatThrownBy(() -> soloSabados.crear(solicitud(VIERNES, null)))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessage("Solo se pueden registrar eventos en sábado.");
        assertThat(soloSabados.crear(solicitud(SABADO, null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
    }

    @Test
    void sinInvitadosEsInvalido() {
        CrearEventoRequest vacio = new CrearEventoRequest("Boda", clienteId, SABADO, TipoEvento.BODA, 0, 0, ZonaEvento.NEIVA, null);
        assertThatThrownBy(() -> service.crear(vacio)).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void clienteInexistenteDa404() {
        when(clientesClient.existe(clienteId)).thenReturn(false);
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, null)))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining(clienteId.toString());
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void fechaPendienteDa409() {
        when(fechaRepository.findAllByFechaIn(anyCollection())).thenReturn(List.of(registro(SABADO, EstadoFecha.PENDIENTE)));
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está disponible");
    }

    @Test
    void fechaOcupadaDa409() {
        when(fechaRepository.findAllByFechaIn(anyCollection())).thenReturn(List.of(registro(SABADO, EstadoFecha.OCUPADA)));
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, null))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void otroEventoElMismoFinDeSemanaDa409() {
        when(fechaRepository.findAllByFechaIn(anyCollection())).thenReturn(List.of(registro(VIERNES, EstadoFecha.PENDIENTE)));
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, null)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mismo fin de semana");
        verify(eventoRepository, never()).save(any());
    }

    @Test
    void siSeConfiguraUnDiaEntreSemanaNoImportanLosDiasVecinos() {
        EventoService conMiercoles = servicio(DiasPermitidos.desde("WEDNESDAY,FRIDAY,SATURDAY,SUNDAY"));
        when(fechaRepository.findAllByFechaIn(anyCollection()))
                .thenReturn(List.of(registro(MIERCOLES.minusDays(1), EstadoFecha.OCUPADA)));
        assertThat(conMiercoles.crear(solicitud(MIERCOLES, null)).estado()).isEqualTo(EstadoEvento.PENDIENTE);
    }

    @Test
    void lugarInexistenteDa404() {
        UUID lugarId = UUID.randomUUID();
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, lugarId))).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void lugarConCapacidadInsuficienteDa409() {
        UUID lugarId = UUID.randomUUID();
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.of(new LugarRemoto(lugarId, "Salón Pequeño", 50, null)));
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, lugarId)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("capacidad para 50");
    }

    @Test
    void lugarInactivoDa409() {
        UUID lugarId = UUID.randomUUID();
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.of(new LugarRemoto(lugarId, "Hacienda", 300, "INACTIVO")));
        assertThatThrownBy(() -> service.crear(solicitud(SABADO, lugarId)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("INACTIVO");
    }

    @Test
    void lugarSinEstadoNiCapacidadNoSeValida() {
        // Lugares todavía no envía estado (Fase 4): la regla solo aplica si el campo viene
        UUID lugarId = UUID.randomUUID();
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.of(new LugarRemoto(lugarId, "Hacienda", null, null)));
        assertThat(service.crear(solicitud(SABADO, lugarId)).lugarId()).isEqualTo(lugarId);
    }
}

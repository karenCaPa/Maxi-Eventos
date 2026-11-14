package com.maxieventos.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.eventos.dto.EventoResponse;
import com.maxieventos.eventos.dto.remoto.BalanceRemoto;
import com.maxieventos.eventos.dto.remoto.ContratoRemoto;
import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.FechaAgenda;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.entity.ZonaEvento;
import com.maxieventos.eventos.exception.ReglaNegocioException;
import com.maxieventos.eventos.exception.ServicioNoDisponibleException;
import com.maxieventos.eventos.kafka.EventoConfirmadoInterno;
import com.maxieventos.eventos.repository.EventoRepository;
import com.maxieventos.eventos.repository.FechaAgendaRepository;
import com.maxieventos.eventos.service.remoto.ClientesClient;
import com.maxieventos.eventos.service.remoto.ContratosClient;
import com.maxieventos.eventos.service.remoto.FinanzasClient;
import com.maxieventos.eventos.service.remoto.LugaresClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

@DisplayName("Confirmación de un evento (contrato FIRMADO + reserva abonada)")
class EventoServiceConfirmacionTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 12, 5);

    private EventoRepository eventoRepository;
    private FechaAgendaRepository fechaRepository;
    private ContratosClient contratosClient;
    private FinanzasClient finanzasClient;
    private ApplicationEventPublisher publicador;
    private EventoService service;
    private Evento evento;
    private UUID eventoId;

    @BeforeEach
    void preparar() {
        eventoRepository = mock(EventoRepository.class);
        fechaRepository = mock(FechaAgendaRepository.class);
        contratosClient = mock(ContratosClient.class);
        finanzasClient = mock(FinanzasClient.class);
        publicador = mock(ApplicationEventPublisher.class);
        TransactionTemplate transaccion = new TransactionTemplate(mock(PlatformTransactionManager.class));
        Clock reloj = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Bogota"));
        service = new EventoService(eventoRepository, fechaRepository, mock(ClientesClient.class),
                mock(LugaresClient.class), contratosClient, finanzasClient, transaccion, publicador, reloj,
                DiasPermitidos.FIN_DE_SEMANA);

        evento = new Evento("Boda de Ana y Luis", FECHA, UUID.randomUUID(), TipoEvento.BODA, 100, 20, ZonaEvento.RURAL, null);
        eventoId = UUID.randomUUID();
        ReflectionTestUtils.setField(evento, "id", eventoId);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fechaRepository.findById(FECHA)).thenReturn(Optional.of(fechaPendiente()));
        when(fechaRepository.save(any(FechaAgenda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private FechaAgenda fechaPendiente() {
        FechaAgenda agenda = new FechaAgenda(FECHA);
        agenda.asignar(EstadoFecha.PENDIENTE, eventoId);
        return agenda;
    }

    private void contrato(String estado) {
        when(contratosClient.obtenerDeEvento(eventoId)).thenReturn(Optional.of(new ContratoRemoto(UUID.randomUUID(), eventoId, estado)));
    }

    private void balance(String abonado, String reserva) {
        when(finanzasClient.obtenerBalance(eventoId)).thenReturn(Optional.of(
                new BalanceRemoto(eventoId, new BigDecimal(abonado), new BigDecimal(reserva), "COP")));
    }

    private void verificarQueNoSeConfirmo() {
        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PENDIENTE);
        verify(eventoRepository, never()).save(any());
        verify(fechaRepository, never()).save(any());
        verify(publicador, never()).publishEvent(any());
    }

    @Test
    void sinContratoDa409() {
        when(contratosClient.obtenerDeEvento(eventoId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene contrato");
        verificarQueNoSeConfirmo();
    }

    @Test
    void conContratoSinFirmarDa409() {
        // Estado inicial de los contratos desde la Fase 4 (antes BORRADOR)
        contrato("PENDIENTE");
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("PENDIENTE").hasMessageContaining("FIRMADO");
        verifyNoInteractions(finanzasClient);
        verificarQueNoSeConfirmo();
    }

    @Test
    void sinCotizacionDa409() {
        contrato("FIRMADO");
        when(finanzasClient.obtenerBalance(eventoId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene cotización");
        verificarQueNoSeConfirmo();
    }

    @Test
    void conAbonoInsuficienteDa409ConElFaltante() {
        contrato("FIRMADO");
        balance("1000000.00", "5220000.00");
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no alcanza el monto de reserva requerido")
                .hasMessageContaining("Faltan 4220000.00 COP");
        verificarQueNoSeConfirmo();
    }

    @Test
    void siContratosNoRespondeDa503() {
        when(contratosClient.obtenerDeEvento(eventoId))
                .thenThrow(new ServicioNoDisponibleException("El servicio de contratos no está disponible", null));
        assertThatThrownBy(() -> service.confirmar(eventoId)).isInstanceOf(ServicioNoDisponibleException.class);
        verificarQueNoSeConfirmo();
    }

    @Test
    void siFinanzasNoRespondeDa503() {
        contrato("FIRMADO");
        when(finanzasClient.obtenerBalance(eventoId))
                .thenThrow(new ServicioNoDisponibleException("El servicio de finanzas no está disponible", null));
        assertThatThrownBy(() -> service.confirmar(eventoId)).isInstanceOf(ServicioNoDisponibleException.class);
        verificarQueNoSeConfirmo();
    }

    @Test
    void unEventoYaReservadoNoSeVuelveAConfirmar() {
        evento.confirmar();
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está confirmado");
        verifyNoInteractions(contratosClient, finanzasClient, publicador);
    }

    @Test
    void unEventoCanceladoNoSePuedeConfirmar() {
        evento.cancelar();
        assertThatThrownBy(() -> service.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cancelado");
        verifyNoInteractions(contratosClient, finanzasClient, publicador);
    }

    /** Fase 11I: el mismo servicio con "hoy" fijado en el día dado (Clock fijo en Neiva, 10:00). */
    private EventoService servicioConHoy(LocalDate hoy) {
        ZoneId neiva = ZoneId.of("America/Bogota");
        Clock reloj = Clock.fixed(hoy.atTime(10, 0).atZone(neiva).toInstant(), neiva);
        return new EventoService(eventoRepository, fechaRepository, mock(ClientesClient.class),
                mock(LugaresClient.class), contratosClient, finanzasClient,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), publicador, reloj,
                DiasPermitidos.FIN_DE_SEMANA);
    }

    @Test
    @DisplayName("Fase 11I: un evento cuya fecha ya pasó no se confirma (409) y ni se consulta a Contratos ni a Finanzas")
    void conLaFechaPasadaDa409() {
        contrato("FIRMADO");
        balance("5220000.00", "5220000.00");
        EventoService alDiaSiguiente = servicioConHoy(FECHA.plusDays(1));

        assertThatThrownBy(() -> alDiaSiguiente.confirmar(eventoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No se puede confirmar un evento cuya fecha ya pasó.");
        verifyNoInteractions(contratosClient, finanzasClient);
        verificarQueNoSeConfirmo();
    }

    @Test
    @DisplayName("Fase 11I: el mismo día del evento todavía se puede confirmar (caso límite)")
    void elMismoDiaDelEventoSeConfirma() {
        contrato("FIRMADO");
        balance("5220000.00", "5220000.00");

        assertThat(servicioConHoy(FECHA).confirmar(eventoId).estado()).isEqualTo(EstadoEvento.RESERVADO);
    }

    @Test
    void conContratoFirmadoYReservaAbonadaSeConfirma() {
        contrato("FIRMADO");
        balance("5220000.00", "5220000.00"); // abono exactamente igual a la reserva: es suficiente

        EventoResponse respuesta = service.confirmar(eventoId);

        assertThat(respuesta.estado()).isEqualTo(EstadoEvento.RESERVADO);
        ArgumentCaptor<FechaAgenda> fecha = ArgumentCaptor.forClass(FechaAgenda.class);
        verify(fechaRepository).save(fecha.capture());
        assertThat(fecha.getValue().getEstado()).isEqualTo(EstadoFecha.OCUPADA);
        assertThat(fecha.getValue().getEventoId()).isEqualTo(eventoId);

        ArgumentCaptor<EventoConfirmadoInterno> publicado = ArgumentCaptor.forClass(EventoConfirmadoInterno.class);
        verify(publicador).publishEvent(publicado.capture());
        // Sobre común (Fase 5)
        var mensaje = publicado.getValue().mensaje();
        assertThat(mensaje.idEvento()).isNotNull();
        assertThat(mensaje.tipo()).isEqualTo("EVENTO_CONFIRMADO");
        assertThat(mensaje.version()).isEqualTo(1);
        assertThat(mensaje.servicioOrigen()).isEqualTo("eventos-service");
        assertThat(mensaje.fechaOcurrencia()).isNotNull();
        assertThat(mensaje.eventoId()).isEqualTo(eventoId);
        var datos = mensaje.datos();
        assertThat(datos.eventoId()).isEqualTo(eventoId);
        assertThat(datos.nombreEvento()).isEqualTo("Boda de Ana y Luis");
        assertThat(datos.tipoEvento()).isEqualTo("BODA");
        assertThat(datos.numInvitados()).isEqualTo(120);
        assertThat(datos.zona()).isEqualTo("RURAL");
        assertThat(datos.fecha()).isEqualTo(FECHA);
    }
}

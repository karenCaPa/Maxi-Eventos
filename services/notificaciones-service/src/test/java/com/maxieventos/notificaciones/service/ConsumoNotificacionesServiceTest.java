package com.maxieventos.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxieventos.notificaciones.entity.EstadoNotificacion;
import com.maxieventos.notificaciones.entity.EventoProcesado;
import com.maxieventos.notificaciones.entity.Notificacion;
import com.maxieventos.notificaciones.kafka.MensajeInvalidoException;
import com.maxieventos.notificaciones.kafka.MensajeKafka;
import com.maxieventos.notificaciones.kafka.RegistroConsumo;
import com.maxieventos.notificaciones.repository.EventoProcesadoRepository;
import com.maxieventos.notificaciones.repository.NotificacionRepository;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ConsumoNotificacionesServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private EventoProcesadoRepository procesadoRepository;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ConsumoNotificacionesService service;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID idEvento = UUID.randomUUID();

    @BeforeEach
    void crear() {
        service = new ConsumoNotificacionesService(notificacionRepository, procesadoRepository,
                new RedactorNotificaciones("America/Bogota"), new TransactionTemplate(mock(PlatformTransactionManager.class)),
                mapper);
    }

    private ConsumerRecord<String, MensajeKafka> registro(String topico, String tipo, UUID eventoDelSobre, String datos)
            throws Exception {
        MensajeKafka mensaje = mapper.readValue("""
                {"idEvento":"%s","tipo":"%s","version":1,"fechaOcurrencia":"2026-10-28T10:00:00Z",
                 "servicioOrigen":"x","eventoId":%s,"datos":%s}
                """.formatted(idEvento, tipo, eventoDelSobre == null ? "null" : "\"" + eventoDelSobre + "\"", datos),
                MensajeKafka.class);
        return new ConsumerRecord<>(topico, 1, 12L, eventoId.toString(), mensaje);
    }

    private ConsumerRecord<String, MensajeKafka> eventoConfirmado(UUID cliente) throws Exception {
        return registro("eventos.confirmados", "EVENTO_CONFIRMADO", eventoId, """
                {"eventoId":"%s","nombreEvento":"Boda de Ana y Luis","tipoEvento":"BODA","fecha":"2026-12-05",
                 "clienteId":%s,"lugarId":null,"cantidadAdultos":100,"cantidadNinos":10,"numInvitados":110,"zona":"RURAL"}
                """.formatted(eventoId, cliente == null ? "null" : "\"" + cliente + "\""));
    }

    private ConsumerRecord<String, MensajeKafka> pago(String monto) throws Exception {
        return registro("pagos.registrados", "PAGO_REGISTRADO", eventoId, """
                {"pagoId":"%s","eventoId":"%s","clienteId":"%s","monto":%s,"moneda":"COP","metodoPago":"EFECTIVO",
                 "claveIdempotencia":"k-1","fechaPago":"2026-10-28T15:30:00Z"}
                """.formatted(UUID.randomUUID(), eventoId, clienteId, monto));
    }

    @Test
    void eventoConfirmadoGeneraUnaNotificacionParaElCliente() throws Exception {
        RegistroConsumo.Resultado resultado = service.procesarEventoConfirmado(eventoConfirmado(clienteId));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(guardada.capture());
        Notificacion n = guardada.getValue();
        assertThat(n.getDestinatario()).isEqualTo("cliente:" + clienteId);
        assertThat(n.getAsunto()).isEqualTo("Evento confirmado: Boda de Ana y Luis");
        assertThat(n.getMensaje()).contains("sábado 5 de diciembre de 2026");
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.GENERADA);
        assertThat(n.getEventoId()).isEqualTo(eventoId);
        assertThat(n.getIdEvento()).isEqualTo(idEvento);
        assertThat(n.getTipo()).isEqualTo("EVENTO_CONFIRMADO");
        ArgumentCaptor<EventoProcesado> procesado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(procesadoRepository).save(procesado.capture());
        assertThat(procesado.getValue().getConsumerGroup()).isEqualTo("notificaciones-group");
    }

    @Test
    void pagoGeneraUnaNotificacionDeAbono() throws Exception {
        RegistroConsumo.Resultado resultado = service.procesarPagoRegistrado(pago("1000000"));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getAsunto()).isEqualTo("Abono registrado");
        assertThat(guardada.getValue().getMensaje()).contains("1.000.000,00 COP (EFECTIVO)");
        assertThat(guardada.getValue().getTipo()).isEqualTo("PAGO_REGISTRADO");
    }

    @Test
    void duplicadoSeIgnora() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesarPagoRegistrado(pago("1000000"));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void carreraDetectadaPorLaBdEsDuplicado() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(false, true);
        when(notificacionRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk"));

        RegistroConsumo.Resultado resultado = service.procesarEventoConfirmado(eventoConfirmado(clienteId));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
    }

    @Test
    void mensajesInvalidos() throws Exception {
        var sinCliente = eventoConfirmado(null);
        var montoCero = pago("0");
        var otroTipo = registro("pagos.registrados", "EVENTO_CONFIRMADO", eventoId, "{}");
        var sinEventoId = registro("eventos.confirmados", "EVENTO_CONFIRMADO", null, "{}");

        assertThatThrownBy(() -> service.procesarEventoConfirmado(sinCliente)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesarPagoRegistrado(montoCero)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesarPagoRegistrado(otroTipo)).isInstanceOf(MensajeInvalidoException.class)
                .hasMessageContaining("se esperaba PAGO_REGISTRADO");
        assertThatThrownBy(() -> service.procesarEventoConfirmado(sinEventoId)).isInstanceOf(MensajeInvalidoException.class);
        verify(notificacionRepository, never()).save(any());
        verify(procesadoRepository, never()).save(any());
    }
}

package com.maxieventos.finanzas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.finanzas.config.ConfiguracionCotizacion.ModoCupo;
import com.maxieventos.finanzas.dto.PagoRequest;
import com.maxieventos.finanzas.dto.remoto.EventoRemoto;
import com.maxieventos.finanzas.entity.Cotizacion;
import com.maxieventos.finanzas.entity.Pago;
import com.maxieventos.finanzas.entity.TipoEvento;
import com.maxieventos.finanzas.entity.ZonaEvento;
import com.maxieventos.finanzas.exception.RecursoNoEncontradoException;
import com.maxieventos.finanzas.exception.ReglaNegocioException;
import com.maxieventos.finanzas.exception.SolicitudInvalidaException;
import com.maxieventos.finanzas.kafka.DatosPagoRegistrado;
import com.maxieventos.finanzas.kafka.MensajeInterno;
import com.maxieventos.finanzas.repository.AdicionalEventoRepository;
import com.maxieventos.finanzas.repository.CotizacionRepository;
import com.maxieventos.finanzas.repository.PagoRepository;
import com.maxieventos.finanzas.service.remoto.EventosClient;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    private static final String CLAVE = "clave-de-prueba-1";

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private CotizacionRepository cotizacionRepository;
    @Mock
    private AdicionalEventoRepository adicionalRepository;
    @Mock
    private EventosClient eventosClient;
    @Mock
    private ApplicationEventPublisher publicador;

    private PagoService service;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    /** Total de la cotización de ejemplo: 8.700.000 COP. */
    private Cotizacion cotizacion;

    @BeforeEach
    void crear() {
        service = new PagoService(pagoRepository, cotizacionRepository, adicionalRepository, eventosClient,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), publicador);
        cotizacion = new Cotizacion(eventoId, CalculadoraCotizacionTest.calculadora(ModoCupo.SEPARADO)
                .calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 90, 15), "COP");
    }

    private PagoRequest pago(String monto) {
        return new PagoRequest(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal(monto), "COP", null);
    }

    private void eventoPendiente() {
        when(eventosClient.obtener(eventoId)).thenReturn(new EventoRemoto(eventoId, "Quince", clienteId,
                TipoEvento.QUINCE_ANOS, 90, 15, ZonaEvento.NEIVA, "PENDIENTE"));
    }

    /** Saldo = 8.700.000 + adicionales − abonado. */
    private void saldo(String adicionales, String abonado) {
        when(cotizacionRepository.bloquearPorEventoId(eventoId)).thenReturn(Optional.of(cotizacion));
        when(adicionalRepository.totalPorEvento(eventoId)).thenReturn(new BigDecimal(adicionales));
        when(pagoRepository.totalAbonado(eventoId)).thenReturn(new BigDecimal(abonado));
    }

    private void guardarDevuelveElPago() {
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ------------------------------------------------------------------ pago válido

    @Test
    void pagoValidoSeRegistraYSePublicaTrasElCommit() {
        eventoPendiente();
        saldo("0", "0");
        guardarDevuelveElPago();

        PagoService.RegistroPago registro = service.registrarPago(pago("2610000"), CLAVE);

        assertThat(registro.duplicado()).isFalse();
        assertThat(registro.pago().monto()).isEqualByComparingTo("2610000");
        assertThat(registro.pago().eventIdempotencia()).isEqualTo(CLAVE);
        ArgumentCaptor<MensajeInterno> evento = ArgumentCaptor.forClass(MensajeInterno.class);
        verify(publicador).publishEvent(evento.capture());
        assertThat(evento.getValue().topico()).isEqualTo("pagos.registrados");
        assertThat(evento.getValue().mensaje().tipo()).isEqualTo("PAGO_REGISTRADO");
        assertThat(evento.getValue().mensaje().eventoId()).isEqualTo(eventoId);
        DatosPagoRegistrado datos = (DatosPagoRegistrado) evento.getValue().mensaje().datos();
        assertThat(datos.claveIdempotencia()).isEqualTo(CLAVE);
        assertThat(datos.monto()).isEqualByComparingTo("2610000");
    }

    @Test
    void pagarExactamenteElSaldoEsValido() {
        eventoPendiente();
        saldo("0", "8000000");
        guardarDevuelveElPago();

        assertThat(service.registrarPago(pago("700000"), CLAVE).duplicado()).isFalse();
    }

    @Test
    void elSaldoIncluyeLosServiciosAdicionales() {
        eventoPendiente();
        saldo("300000", "8700000");
        guardarDevuelveElPago();

        assertThat(service.registrarPago(pago("300000"), CLAVE).pago().monto()).isEqualByComparingTo("300000");
    }

    // ------------------------------------------------------------------ monto y saldo

    @Test
    void montoMayorQueElSaldoDa409() {
        eventoPendiente();
        saldo("300000", "8700000");

        assertThatThrownBy(() -> service.registrarPago(pago("300000.01"), CLAVE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("supera el saldo pendiente de 300000");
        verify(pagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(publicador);
    }

    @Test
    void eventoYaPagadoDa409() {
        eventoPendiente();
        saldo("0", "8700000");

        assertThatThrownBy(() -> service.registrarPago(pago("1"), CLAVE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("pagado por completo");
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ evento, cliente, cotización y moneda

    @Test
    void eventoInexistenteDa404() {
        when(eventosClient.obtener(eventoId)).thenThrow(new RecursoNoEncontradoException("No existe un evento con id " + eventoId));

        assertThatThrownBy(() -> service.registrarPago(pago("1000"), CLAVE)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    @Test
    void eventoCanceladoDa409() {
        when(eventosClient.obtener(eventoId)).thenReturn(new EventoRemoto(eventoId, "Quince", clienteId,
                TipoEvento.QUINCE_ANOS, 90, 15, ZonaEvento.NEIVA, "CANCELADO"));

        assertThatThrownBy(() -> service.registrarPago(pago("1000"), CLAVE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("CANCELADO");
    }

    @Test
    void clienteDistintoAlDelEventoDa400() {
        when(eventosClient.obtener(eventoId)).thenReturn(new EventoRemoto(eventoId, "Quince", UUID.randomUUID(),
                TipoEvento.QUINCE_ANOS, 90, 15, ZonaEvento.NEIVA, "PENDIENTE"));

        assertThatThrownBy(() -> service.registrarPago(pago("1000"), CLAVE))
                .isInstanceOfSatisfying(SolicitudInvalidaException.class,
                        ex -> assertThat(ex.getCampo()).isEqualTo("clienteId"));
    }

    @Test
    void sinCotizacionDa409() {
        eventoPendiente();
        when(cotizacionRepository.bloquearPorEventoId(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarPago(pago("1000"), CLAVE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no tiene cotización");
    }

    @Test
    void monedaDistintaALaDeLaCotizacionDa400() {
        eventoPendiente();
        when(cotizacionRepository.bloquearPorEventoId(eventoId)).thenReturn(Optional.of(cotizacion));

        assertThatThrownBy(() -> service.registrarPago(
                new PagoRequest(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal("1000"), "USD", null), CLAVE))
                .isInstanceOfSatisfying(SolicitudInvalidaException.class,
                        ex -> assertThat(ex.getCampo()).isEqualTo("moneda"));
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ idempotencia

    @Test
    void mismaClaveYMismosDatosDevuelveElPagoRegistradoSinConsultarEventos() {
        Pago previo = new Pago(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal("1000.00"), "COP", CLAVE);
        when(pagoRepository.findByClaveIdempotencia(CLAVE)).thenReturn(Optional.of(previo));

        PagoService.RegistroPago registro = service.registrarPago(pago("1000"), CLAVE);

        assertThat(registro.duplicado()).isTrue();
        verifyNoInteractions(eventosClient, publicador);
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    @Test
    void mismaClaveConOtroMontoDa409() {
        Pago previo = new Pago(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal("1000"), "COP", CLAVE);
        when(pagoRepository.findByClaveIdempotencia(CLAVE)).thenReturn(Optional.of(previo));

        assertThatThrownBy(() -> service.registrarPago(pago("2000"), CLAVE))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya se usó para otro pago");
    }

    @Test
    void repeticionConcurrenteDetectadaPorLaRestriccionUnique() {
        Pago otro = new Pago(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal("1000"), "COP", CLAVE);
        when(pagoRepository.findByClaveIdempotencia(CLAVE)).thenReturn(Optional.empty(), Optional.empty(), Optional.of(otro));
        eventoPendiente();
        saldo("0", "0");
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenThrow(new DataIntegrityViolationException("uk_pago_clave_idempotencia"));

        PagoService.RegistroPago registro = service.registrarPago(pago("1000"), CLAVE);

        assertThat(registro.duplicado()).isTrue();
        verifyNoInteractions(publicador);
    }

    @Test
    void repeticionDetectadaAlTomarElBloqueoNoGuardaOtroPago() {
        Pago otro = new Pago(eventoId, clienteId, "TRANSFERENCIA", new BigDecimal("1000"), "COP", CLAVE);
        when(pagoRepository.findByClaveIdempotencia(CLAVE)).thenReturn(Optional.empty(), Optional.of(otro));
        eventoPendiente();
        when(cotizacionRepository.bloquearPorEventoId(eventoId)).thenReturn(Optional.of(cotizacion));

        PagoService.RegistroPago registro = service.registrarPago(pago("1000"), CLAVE);

        assertThat(registro.duplicado()).isTrue();
        verify(pagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(publicador);
    }
}

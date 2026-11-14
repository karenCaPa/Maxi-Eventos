package com.maxieventos.finanzas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.finanzas.config.ConfiguracionCotizacion.ModoCupo;
import com.maxieventos.finanzas.dto.CotizacionResponseDTO;
import com.maxieventos.finanzas.dto.ResultadoCreacion;
import com.maxieventos.finanzas.dto.remoto.EventoRemoto;
import com.maxieventos.finanzas.entity.Cotizacion;
import com.maxieventos.finanzas.entity.TipoEvento;
import com.maxieventos.finanzas.entity.ZonaEvento;
import com.maxieventos.finanzas.exception.RecursoNoEncontradoException;
import com.maxieventos.finanzas.exception.ReglaNegocioException;
import com.maxieventos.finanzas.exception.ServicioNoDisponibleException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CotizacionServiceTest {

    @Mock
    private CotizacionRepository cotizacionRepository;
    @Mock
    private AdicionalEventoRepository adicionalRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private EventosClient eventosClient;

    private CotizacionService service;
    private final UUID eventoId = UUID.randomUUID();

    @BeforeEach
    void crear() {
        service = new CotizacionService(cotizacionRepository, adicionalRepository, pagoRepository, eventosClient,
                CalculadoraCotizacionTest.calculadora(ModoCupo.SEPARADO), "COP");
    }

    private EventoRemoto evento(String estado) {
        return new EventoRemoto(eventoId, "Quince años de Lucía", UUID.randomUUID(), TipoEvento.QUINCE_ANOS, 90, 15,
                ZonaEvento.NEIVA, estado);
    }

    @Test
    void cotizaConLosDatosDelEventoDeEventosService() {
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(evento("PENDIENTE"));
        when(cotizacionRepository.saveAndFlush(any(Cotizacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ResultadoCreacion<CotizacionResponseDTO> resultado = service.cotizar(eventoId);

        assertThat(resultado.creado()).isTrue();
        CotizacionResponseDTO cotizacion = resultado.recurso();
        assertThat(cotizacion.tipoEvento()).isEqualTo(TipoEvento.QUINCE_ANOS);
        assertThat(cotizacion.zona()).isEqualTo("NEIVA");
        assertThat(cotizacion.cantidadAdultos()).isEqualTo(90);
        assertThat(cotizacion.cantidadNinos()).isEqualTo(15);
        assertThat(cotizacion.invitados()).isEqualTo(105);
        assertThat(cotizacion.adultosAdicionales()).isEqualTo(10);
        assertThat(cotizacion.total()).isEqualByComparingTo("8700000");
        assertThat(cotizacion.montoReservaRequerido()).isEqualByComparingTo("2610000");
        assertThat(cotizacion.moneda()).isEqualTo("COP");
    }

    @Test
    void siYaTieneCotizacionLaDevuelveSinRegenerarNiConsultarEventos() {
        Cotizacion existente = cotizacionGuardada();
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.of(existente));

        ResultadoCreacion<CotizacionResponseDTO> resultado = service.cotizar(eventoId);

        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.recurso().total()).isEqualByComparingTo(existente.getTotal());
        verifyNoInteractions(eventosClient);
        verify(cotizacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void eventoCanceladoNoSeCotiza() {
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(evento("CANCELADO"));

        assertThatThrownBy(() -> service.cotizar(eventoId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("CANCELADO");
        verify(cotizacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void eventoInexistenteDa404() {
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenThrow(new RecursoNoEncontradoException("No existe un evento con id " + eventoId));

        assertThatThrownBy(() -> service.cotizar(eventoId)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eventosCaidoDa503() {
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenThrow(new ServicioNoDisponibleException("caído", null));

        assertThatThrownBy(() -> service.cotizar(eventoId)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void dosCotizacionesSimultaneasDevuelvenLaQueSeGuardoPrimero() {
        Cotizacion otra = cotizacionGuardada();
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.empty(), Optional.of(otra));
        when(eventosClient.obtener(eventoId)).thenReturn(evento("PENDIENTE"));
        when(cotizacionRepository.saveAndFlush(any(Cotizacion.class))).thenThrow(new DataIntegrityViolationException("uk"));

        ResultadoCreacion<CotizacionResponseDTO> resultado = service.cotizar(eventoId);

        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.recurso().total()).isEqualByComparingTo(otra.getTotal());
    }

    @Test
    void balanceSumaAdicionalesYRestaLoAbonado() {
        when(cotizacionRepository.findByEventoId(eventoId)).thenReturn(Optional.of(cotizacionGuardada()));
        when(adicionalRepository.totalPorEvento(eventoId)).thenReturn(new BigDecimal("300000"));
        when(pagoRepository.totalAbonado(eventoId)).thenReturn(new BigDecimal("2000000"));

        var balance = service.obtenerBalance(eventoId);

        assertThat(balance.total()).isEqualByComparingTo("9000000");
        assertThat(balance.saldoPendiente()).isEqualByComparingTo("7000000");
        assertThat(balance.montoReservaRequerido()).isEqualByComparingTo("2610000");
    }

    private Cotizacion cotizacionGuardada() {
        var desglose = CalculadoraCotizacionTest.calculadora(ModoCupo.SEPARADO)
                .calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 90, 15);
        return new Cotizacion(eventoId, desglose, "COP");
    }
}

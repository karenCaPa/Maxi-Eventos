package com.maxieventos.contratos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.contratos.dto.ContratoResponseDTO;
import com.maxieventos.contratos.dto.ResultadoCreacion;
import com.maxieventos.contratos.dto.remoto.ClienteRemoto;
import com.maxieventos.contratos.dto.remoto.CotizacionRemota;
import com.maxieventos.contratos.dto.remoto.EventoRemoto;
import com.maxieventos.contratos.dto.remoto.LugarRemoto;
import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.ContratoAdicional;
import com.maxieventos.contratos.entity.EstadoContrato;
import com.maxieventos.contratos.repository.ContratoAdicionalRepository;
import com.maxieventos.contratos.exception.RecursoNoEncontradoException;
import com.maxieventos.contratos.exception.ReglaNegocioException;
import com.maxieventos.contratos.exception.ServicioNoDisponibleException;
import com.maxieventos.contratos.repository.ContratoRepository;
import com.maxieventos.contratos.service.remoto.ClientesClient;
import com.maxieventos.contratos.service.remoto.EventosClient;
import com.maxieventos.contratos.service.remoto.FinanzasClient;
import com.maxieventos.contratos.service.remoto.LugaresClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ContratosServiceTest {

    @Mock
    private ContratoRepository contratoRepository;
    @Mock
    private ContratoAdicionalRepository adicionalRepository;
    @Mock
    private PdfGeneratorService pdfGeneratorService;
    @Mock
    private EventosClient eventosClient;
    @Mock
    private ClientesClient clientesClient;
    @Mock
    private LugaresClient lugaresClient;
    @Mock
    private FinanzasClient finanzasClient;

    @InjectMocks
    private ContratosService service;

    private final UUID eventoId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();
    private final UUID lugarId = UUID.randomUUID();
    private final LocalDate fecha = LocalDate.of(2026, 11, 14);

    private EventoRemoto evento(String estado, UUID lugar) {
        return new EventoRemoto(eventoId, "Boda Peña y Muñoz", fecha, clienteId, "BODA", 120, 12, "RURAL", lugar, estado);
    }

    private void datosCompletos(UUID lugar) {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(Optional.of(evento("PENDIENTE", lugar)));
        when(finanzasClient.cotizacion(eventoId)).thenReturn(Optional.of(
                new CotizacionRemota(UUID.randomUUID(), eventoId, new BigDecimal("15290000.00"), "COP")));
        when(clientesClient.obtener(clienteId)).thenReturn(Optional.of(
                new ClienteRemoto(clienteId, "José Peña Muñoz", "3001234567", "jose.pena@ejemplo.com")));
    }

    // ------------------------------------------------------------------ crear con la foto de los datos

    @Test
    void creaElContratoPendienteConLosDatosDeLosCuatroServicios() {
        datosCompletos(lugarId);
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.of(new LugarRemoto(lugarId, "Salón Los Ángeles", "Calle 5 # 3-20")));
        when(contratoRepository.saveAndFlush(any(Contrato.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ResultadoCreacion<ContratoResponseDTO> resultado = service.crear(eventoId);

        assertThat(resultado.creado()).isTrue();
        ArgumentCaptor<Contrato> guardado = ArgumentCaptor.forClass(Contrato.class);
        verify(contratoRepository).saveAndFlush(guardado.capture());
        Contrato c = guardado.getValue();
        assertThat(c.getEstado()).isEqualTo(EstadoContrato.PENDIENTE);
        assertThat(c.getNombreEvento()).isEqualTo("Boda Peña y Muñoz");
        assertThat(c.getClienteNombre()).isEqualTo("José Peña Muñoz");
        assertThat(c.getClienteCorreo()).isEqualTo("jose.pena@ejemplo.com");
        assertThat(c.getFechaEvento()).isEqualTo(fecha);
        assertThat(c.getTipoEvento()).isEqualTo("BODA");
        assertThat(c.getCantidadAdultos()).isEqualTo(120);
        assertThat(c.getCantidadNinos()).isEqualTo(12);
        assertThat(c.getLugarNombre()).isEqualTo("Salón Los Ángeles");
        assertThat(c.getLugarDireccion()).isEqualTo("Calle 5 # 3-20");
        assertThat(c.getValorTotal()).isEqualByComparingTo("15290000");
        assertThat(c.getMoneda()).isEqualTo("COP");
        assertThat(resultado.recurso().estado()).isEqualTo(EstadoContrato.PENDIENTE);
    }

    @Test
    void eventoSinLugarNoConsultaLugares() {
        datosCompletos(null);
        when(contratoRepository.saveAndFlush(any(Contrato.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ContratoResponseDTO contrato = service.crear(eventoId).recurso();

        assertThat(contrato.lugarNombre()).isNull();
        verifyNoInteractions(lugaresClient);
    }

    @Test
    void siYaTieneContratoLoDevuelveSinConsultarAOtrosServicios() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(new Contrato(eventoId, "Boda")));

        ResultadoCreacion<ContratoResponseDTO> resultado = service.crear(eventoId);

        assertThat(resultado.creado()).isFalse();
        verifyNoInteractions(eventosClient, clientesClient, lugaresClient, finanzasClient);
    }

    @Test
    void eventoInexistenteDa404() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(eventoId)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eventoCanceladoDa409() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(Optional.of(evento("CANCELADO", null)));

        assertThatThrownBy(() -> service.crear(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("CANCELADO");
        verify(contratoRepository, never()).saveAndFlush(any());
    }

    @Test
    void sinCotizacionDa409() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(Optional.of(evento("PENDIENTE", null)));
        when(finanzasClient.cotizacion(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cotización");
        verify(contratoRepository, never()).saveAndFlush(any());
    }

    @Test
    void lugarAsignadoQueYaNoExisteDa409() {
        datosCompletos(lugarId);
        when(lugaresClient.obtener(lugarId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("lugar");
    }

    @Test
    void unServicioCaidoDa503() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());
        when(eventosClient.obtener(eventoId)).thenReturn(Optional.of(evento("PENDIENTE", null)));
        when(finanzasClient.cotizacion(eventoId)).thenThrow(new ServicioNoDisponibleException("finanzas caído", null));

        assertThatThrownBy(() -> service.crear(eventoId)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void dosCreacionesSimultaneasDevuelvenElQueSeGuardoPrimero() {
        datosCompletos(null);
        Contrato otro = new Contrato(eventoId, "Boda Peña y Muñoz");
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty(), Optional.of(otro));
        when(contratoRepository.saveAndFlush(any(Contrato.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThat(service.crear(eventoId).creado()).isFalse();
    }

    // ------------------------------------------------------------------ firma

    @Test
    void firmarUnContratoPendiente() {
        Contrato contrato = new Contrato(eventoId, "Boda");
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(contrato));
        when(contratoRepository.save(contrato)).thenReturn(contrato);

        ContratoResponseDTO firmado = service.marcarFirmado(eventoId);

        assertThat(firmado.estado()).isEqualTo(EstadoContrato.FIRMADO);
        assertThat(firmado.firmadoEn()).isNotNull();
    }

    @Test
    void firmarDosVecesDa409() {
        Contrato contrato = new Contrato(eventoId, "Boda");
        contrato.marcarFirmado();
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(contrato));

        assertThatThrownBy(() -> service.marcarFirmado(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está FIRMADO");
        verify(contratoRepository, never()).save(any());
    }

    @Test
    void firmarSinContratoDa404() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarFirmado(eventoId)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ anexo

    @Test
    void anexoSinAdicionalesDa409() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(new Contrato(eventoId, "Boda")));
        when(adicionalRepository.findByEventoIdOrderByRecibidoEnAsc(eventoId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generarPdfAnexo(eventoId))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene servicios adicionales");
        verifyNoInteractions(pdfGeneratorService);
    }

    @Test
    void anexoSaleDeContratoAdicionalesSinConsultarAFinanzasYNoModificaElContrato() {
        Contrato contrato = new Contrato(eventoId, "Boda");
        List<ContratoAdicional> adicionales = List.of(new ContratoAdicional(UUID.randomUUID(), eventoId, "hora-extra",
                "Hora adicional de servicio", 2, new BigDecimal("120000"), new BigDecimal("240000"), "COP", Instant.now()));
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(contrato));
        when(adicionalRepository.findByEventoIdOrderByRecibidoEnAsc(eventoId)).thenReturn(adicionales);
        when(pdfGeneratorService.generarAnexo(contrato, adicionales)).thenReturn(new byte[] {1});

        assertThat(service.generarPdfAnexo(eventoId)).hasSize(1);
        verifyNoInteractions(finanzasClient);
        verify(contratoRepository, never()).save(any());
        verify(contratoRepository, never()).saveAndFlush(any());
    }

    @Test
    void anexoSinContratoDa404() {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarPdfAnexo(eventoId)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(pdfGeneratorService, never()).generarAnexo(any(), anyList());
    }
}

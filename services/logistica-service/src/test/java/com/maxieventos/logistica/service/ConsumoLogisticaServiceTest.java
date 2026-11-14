package com.maxieventos.logistica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxieventos.logistica.config.EfectosAdicionales;
import com.maxieventos.logistica.config.EfectosAdicionales.Efecto;
import com.maxieventos.logistica.entity.AdicionalLogistica;
import com.maxieventos.logistica.entity.EventoProcesado;
import com.maxieventos.logistica.entity.ItemCompraEntity;
import com.maxieventos.logistica.entity.LogisticaEvento;
import com.maxieventos.logistica.kafka.MensajeInvalidoException;
import com.maxieventos.logistica.kafka.MensajeKafka;
import com.maxieventos.logistica.kafka.RegistroConsumo;
import com.maxieventos.logistica.repository.AdicionalLogisticaRepository;
import com.maxieventos.logistica.repository.EventoProcesadoRepository;
import com.maxieventos.logistica.repository.LogisticaEventoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ConsumoLogisticaServiceTest {

    @Mock
    private LogisticaEventoRepository logisticaRepository;
    @Mock
    private AdicionalLogisticaRepository adicionalRepository;
    @Mock
    private EventoProcesadoRepository procesadoRepository;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ConsumoLogisticaService service;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID idEvento = UUID.randomUUID();

    @BeforeEach
    void crear() {
        EfectosAdicionales efectos = new EfectosAdicionales(Map.of(
                "transporte-adicional", new Efecto(0, 0, 1), "hora-extra", Efecto.NINGUNO));
        service = new ConsumoLogisticaService(logisticaRepository, adicionalRepository, procesadoRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), efectos, mapper);
    }

    private ConsumerRecord<String, MensajeKafka> registro(String topico, String tipo, UUID eventoDelSobre, String datos) throws Exception {
        MensajeKafka mensaje = mapper.readValue("""
                {"idEvento":%s,"tipo":"%s","version":1,"fechaOcurrencia":"2026-10-28T10:00:00Z",
                 "servicioOrigen":"test","eventoId":%s,"datos":%s}
                """.formatted(idEvento == null ? "null" : "\"" + idEvento + "\"", tipo,
                eventoDelSobre == null ? "null" : "\"" + eventoDelSobre + "\"", datos), MensajeKafka.class);
        return new ConsumerRecord<>(topico, 1, 42L, eventoId.toString(), mensaje);
    }

    private ConsumerRecord<String, MensajeKafka> confirmado(int invitados, String tipoEvento, String zona) throws Exception {
        return registro("eventos.confirmados", "EVENTO_CONFIRMADO", eventoId, """
                {"eventoId":"%s","nombreEvento":"Boda Peña","tipoEvento":"%s","fecha":"2026-12-05","numInvitados":%d,
                 "cantidadAdultos":%d,"cantidadNinos":0,"zona":"%s","campoNuevo":"se ignora"}
                """.formatted(eventoId, tipoEvento, invitados, invitados, zona));
    }

    private ConsumerRecord<String, MensajeKafka> adicional(String codigo, int cantidad) throws Exception {
        return registro("servicios-adicionales.agregados", "SERVICIO_ADICIONAL_AGREGADO", eventoId, """
                {"adicionalId":"%s","eventoId":"%s","codigo":"%s","nombre":"X","cantidad":%d,"subtotal":1000,"moneda":"COP"}
                """.formatted(UUID.randomUUID(), eventoId, codigo, cantidad));
    }

    // ------------------------------------------------------------------ EVENTO_CONFIRMADO

    @Test
    void eventoConfirmadoNuevoCalculaLogisticaYRegistraElIdEvento() throws Exception {
        when(logisticaRepository.existsById(eventoId)).thenReturn(false);
        when(adicionalRepository.findByEventoIdAndAplicadoFalse(eventoId)).thenReturn(List.of());

        RegistroConsumo.Resultado resultado = service.procesarEventoConfirmado(confirmado(105, "QUINCE_ANOS", "NEIVA"));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<LogisticaEvento> logistica = ArgumentCaptor.forClass(LogisticaEvento.class);
        verify(logisticaRepository).save(logistica.capture());
        assertThat(logistica.getValue().getMeserosRequeridos()).isEqualTo(7);
        assertThat(logistica.getValue().getPersonalCocinaRequerido()).isEqualTo(4);
        assertThat(logistica.getValue().getCamionesTransporte()).isEqualTo(2);
        assertThat(logistica.getValue().getCompras()).hasSize(4);
        ArgumentCaptor<EventoProcesado> procesado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(procesadoRepository).save(procesado.capture());
        assertThat(procesado.getValue().getIdEvento()).isEqualTo(idEvento);
        assertThat(procesado.getValue().getConsumerGroup()).isEqualTo("logistica-group");
    }

    // Fase 8: límites del cálculo. Meseros = ⌈invitados/15⌉, cocina = ⌈invitados/30⌉, camiones = 1 hasta 100 y 2 desde 101.
    @ParameterizedTest(name = "{0} invitados → {1} meseros, {2} de cocina, {3} camiones")
    @CsvSource({"1, 1, 1, 1", "15, 1, 1, 1", "16, 2, 1, 1", "30, 2, 1, 1", "31, 3, 2, 1", "100, 7, 4, 1", "101, 7, 4, 2",
            "300, 20, 10, 2"})
    void calculoDePersonalYTransporteEnLosLimites(int invitados, int meseros, int cocina, int camiones) throws Exception {
        when(logisticaRepository.existsById(eventoId)).thenReturn(false);
        when(adicionalRepository.findByEventoIdAndAplicadoFalse(eventoId)).thenReturn(List.of());

        service.procesarEventoConfirmado(confirmado(invitados, "QUINCE_ANOS", "NEIVA"));

        ArgumentCaptor<LogisticaEvento> logistica = ArgumentCaptor.forClass(LogisticaEvento.class);
        verify(logisticaRepository).save(logistica.capture());
        assertThat(logistica.getValue().getMeserosRequeridos()).isEqualTo(meseros);
        assertThat(logistica.getValue().getPersonalCocinaRequerido()).isEqualTo(cocina);
        assertThat(logistica.getValue().getCamionesTransporte()).isEqualTo(camiones);
    }

    @Test
    void comprasDeUnaBodaRuralIncluyenCopasYMasHielo() throws Exception {
        when(logisticaRepository.existsById(eventoId)).thenReturn(false);
        when(adicionalRepository.findByEventoIdAndAplicadoFalse(eventoId)).thenReturn(List.of());

        service.procesarEventoConfirmado(confirmado(81, "BODA", "RURAL"));

        ArgumentCaptor<LogisticaEvento> logistica = ArgumentCaptor.forClass(LogisticaEvento.class);
        verify(logisticaRepository).save(logistica.capture());
        assertThat(logistica.getValue().getCompras())
                .extracting(ItemCompraEntity::getNombreItem, ItemCompraEntity::getCantidadEstimada)
                .containsExactly(
                        tuple("Hielo", "11 bolsas"),          // rural: ⌈81/8⌉ (en Neiva sería ⌈81/10⌉ = 9)
                        tuple("Servilletas", "243 unidades"),
                        tuple("Bebidas", "162 unidades"),
                        tuple("Vasos", "162 unidades"),
                        tuple("Copas", "41 unidades"));       // solo en bodas: ⌈81/2⌉
    }

    @Test
    void comprasDeQuinceAnosEnNeivaSinCopas() throws Exception {
        when(logisticaRepository.existsById(eventoId)).thenReturn(false);
        when(adicionalRepository.findByEventoIdAndAplicadoFalse(eventoId)).thenReturn(List.of());

        service.procesarEventoConfirmado(confirmado(81, "QUINCE_ANOS", "NEIVA"));

        ArgumentCaptor<LogisticaEvento> logistica = ArgumentCaptor.forClass(LogisticaEvento.class);
        verify(logisticaRepository).save(logistica.capture());
        assertThat(logistica.getValue().getCompras()).extracting(ItemCompraEntity::getNombreItem)
                .containsExactly("Hielo", "Servilletas", "Bebidas", "Vasos");
        assertThat(logistica.getValue().getCompras().get(0).getCantidadEstimada()).isEqualTo("9 bolsas");
    }

    @Test
    void mismoIdEventoDosVecesSeIgnora() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesarEventoConfirmado(confirmado(105, "BODA", "NEIVA"));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        verify(logisticaRepository, never()).save(any());
        verify(procesadoRepository, never()).save(any());
    }

    @Test
    void otraConfirmacionDelMismoEventoSeIgnoraSoloParaEventoConfirmado() throws Exception {
        when(logisticaRepository.existsById(eventoId)).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesarEventoConfirmado(confirmado(105, "BODA", "NEIVA"));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        assertThat(resultado.detalle()).contains("ya tiene logística");
        verify(logisticaRepository, never()).save(any());
        verify(procesadoRepository).save(any(EventoProcesado.class));
    }

    @Test
    void alConfirmarSeAplicanLosAdicionalesQueLlegaronAntes() throws Exception {
        AdicionalLogistica transporte = new AdicionalLogistica(UUID.randomUUID(), eventoId, "transporte-adicional", 2,
                0, 0, 2, Instant.now());
        when(logisticaRepository.existsById(eventoId)).thenReturn(false);
        when(adicionalRepository.findByEventoIdAndAplicadoFalse(eventoId)).thenReturn(List.of(transporte));

        service.procesarEventoConfirmado(confirmado(80, "QUINCE_ANOS", "NEIVA"));

        ArgumentCaptor<LogisticaEvento> logistica = ArgumentCaptor.forClass(LogisticaEvento.class);
        verify(logisticaRepository).save(logistica.capture());
        assertThat(logistica.getValue().getCamionesTransporte()).isEqualTo(1 + 2);
        assertThat(transporte.isAplicado()).isTrue();
    }

    // ------------------------------------------------------------------ SERVICIO_ADICIONAL_AGREGADO

    @Test
    void transporteSobreEventoYaConfirmadoRecalculaLosCamiones() throws Exception {
        LogisticaEvento existente = new LogisticaEvento(eventoId, 7, 4, 2);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(existente));

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional("transporte-adicional", 2));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        assertThat(resultado.detalle()).contains("7/4/2 -> 7/4/4");
        assertThat(existente.getCamionesTransporte()).isEqualTo(4);
        verify(logisticaRepository).save(existente);
        ArgumentCaptor<AdicionalLogistica> guardado = ArgumentCaptor.forClass(AdicionalLogistica.class);
        verify(adicionalRepository).save(guardado.capture());
        assertThat(guardado.getValue().isAplicado()).isTrue();
        verify(procesadoRepository).save(any(EventoProcesado.class));
    }

    @Test
    void adicionalSinEfectoNoCambiaLaLogistica() throws Exception {
        LogisticaEvento existente = new LogisticaEvento(eventoId, 7, 4, 2);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(existente));

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional("hora-extra", 3));

        assertThat(resultado.detalle()).contains("sin efecto");
        assertThat(existente.getCamionesTransporte()).isEqualTo(2);
        assertThat(existente.getMeserosRequeridos()).isEqualTo(7);
    }

    @Test
    void codigoQueNoEstaEnLaConfiguracionNoTieneEfecto() throws Exception {
        LogisticaEvento existente = new LogisticaEvento(eventoId, 7, 4, 2);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.of(existente));

        service.procesarServicioAdicional(adicional("codigo-nuevo", 1));

        assertThat(existente.getCamionesTransporte()).isEqualTo(2);
    }

    @Test
    void adicionalAntesDeLaConfirmacionSeGuardaSinAplicar() throws Exception {
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.empty());

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional("transporte-adicional", 1));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        assertThat(resultado.detalle()).contains("se aplicará al confirmarlo");
        ArgumentCaptor<AdicionalLogistica> guardado = ArgumentCaptor.forClass(AdicionalLogistica.class);
        verify(adicionalRepository).save(guardado.capture());
        assertThat(guardado.getValue().isAplicado()).isFalse();
        assertThat(guardado.getValue().getCamionesExtra()).isEqualTo(1);
    }

    @Test
    void adicionalDuplicadoSeIgnora() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional("transporte-adicional", 1));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        verify(adicionalRepository, never()).save(any());
        verify(logisticaRepository, never()).findById(any());
    }

    @Test
    void carreraDetectadaPorLaBdEsDuplicado() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(false, true);
        when(logisticaRepository.findById(eventoId)).thenReturn(Optional.empty());
        when(procesadoRepository.save(any())).thenThrow(new DataIntegrityViolationException("pk"));

        assertThat(service.procesarServicioAdicional(adicional("hora-extra", 1)).resultado())
                .isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
    }

    // ------------------------------------------------------------------ mensajes inválidos (no se reintentan)

    @Test
    void sobreSinEventoIdEsInvalido() throws Exception {
        var sinEventoId = registro("eventos.confirmados", "EVENTO_CONFIRMADO", null, "{}");

        assertThatThrownBy(() -> service.procesarEventoConfirmado(sinEventoId)).isInstanceOf(MensajeInvalidoException.class);
    }

    @Test
    void tipoDesconocidoEsInvalido() throws Exception {
        var otro = registro("eventos.confirmados", "TIPO_DESCONOCIDO", eventoId, "{}");

        assertThatThrownBy(() -> service.procesarEventoConfirmado(otro))
                .isInstanceOf(MensajeInvalidoException.class).hasMessageContaining("TIPO_DESCONOCIDO");
    }

    @Test
    void datosInvalidosSonInvalidos() throws Exception {
        assertThatThrownBy(() -> service.procesarEventoConfirmado(confirmado(105, "BODA", "MARTE")))
                .isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesarServicioAdicional(adicional("transporte-adicional", 0)))
                .isInstanceOf(MensajeInvalidoException.class);
        verify(procesadoRepository, never()).save(any());
    }

    @Test
    void mensajeNuloEsInvalido() {
        ConsumerRecord<String, MensajeKafka> vacio = new ConsumerRecord<>("eventos.confirmados", 0, 1L, "k", null);

        assertThatThrownBy(() -> service.procesarEventoConfirmado(vacio)).isInstanceOf(MensajeInvalidoException.class);
    }
}

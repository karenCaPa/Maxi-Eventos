package com.maxieventos.contratos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.ContratoAdicional;
import com.maxieventos.contratos.entity.EventoProcesado;
import com.maxieventos.contratos.kafka.MensajeInvalidoException;
import com.maxieventos.contratos.kafka.MensajeKafka;
import com.maxieventos.contratos.kafka.RegistroConsumo;
import com.maxieventos.contratos.repository.ContratoAdicionalRepository;
import com.maxieventos.contratos.repository.ContratoRepository;
import com.maxieventos.contratos.repository.EventoProcesadoRepository;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ConsumoContratosServiceTest {

    @Mock
    private ContratoAdicionalRepository adicionalRepository;
    @Mock
    private ContratoRepository contratoRepository;
    @Mock
    private EventoProcesadoRepository procesadoRepository;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ConsumoContratosService service;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID idEvento = UUID.randomUUID();
    private final UUID adicionalId = UUID.randomUUID();

    @BeforeEach
    void crear() {
        service = new ConsumoContratosService(adicionalRepository, contratoRepository, procesadoRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mapper);
    }

    private ConsumerRecord<String, MensajeKafka> registro(String tipo, UUID eventoDelSobre, String datos) throws Exception {
        MensajeKafka mensaje = mapper.readValue("""
                {"idEvento":"%s","tipo":"%s","version":1,"fechaOcurrencia":"2026-10-28T10:00:00Z",
                 "servicioOrigen":"finanzas-service","eventoId":%s,"datos":%s}
                """.formatted(idEvento, tipo, eventoDelSobre == null ? "null" : "\"" + eventoDelSobre + "\"", datos),
                MensajeKafka.class);
        return new ConsumerRecord<>("servicios-adicionales.agregados", 0, 9L, eventoId.toString(), mensaje);
    }

    private ConsumerRecord<String, MensajeKafka> adicional() throws Exception {
        return registro("SERVICIO_ADICIONAL_AGREGADO", eventoId, """
                {"adicionalId":"%s","eventoId":"%s","codigo":"decoracion-premium","nombre":"Decoración premium",
                 "cantidad":1,"valorUnitario":250000,"subtotal":250000,"moneda":"COP"}
                """.formatted(adicionalId, eventoId));
    }

    @Test
    void guardaElAdicionalParaElAnexoYRegistraElIdEvento() throws Exception {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.of(new Contrato(eventoId, "Boda")));

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional());

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<ContratoAdicional> guardado = ArgumentCaptor.forClass(ContratoAdicional.class);
        verify(adicionalRepository).save(guardado.capture());
        assertThat(guardado.getValue().getAdicionalId()).isEqualTo(adicionalId);
        assertThat(guardado.getValue().getNombre()).isEqualTo("Decoración premium");
        assertThat(guardado.getValue().getSubtotal()).isEqualByComparingTo("250000");
        ArgumentCaptor<EventoProcesado> procesado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(procesadoRepository).save(procesado.capture());
        assertThat(procesado.getValue().getConsumerGroup()).isEqualTo("contratos-group");
        assertThat(procesado.getValue().getIdEvento()).isEqualTo(idEvento);
    }

    @Test
    void siElContratoAunNoExisteSeGuardaIgualYNoVaAlDlt() throws Exception {
        when(contratoRepository.findByEventoId(eventoId)).thenReturn(Optional.empty());

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional());

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        assertThat(resultado.detalle()).contains("aún no tiene contrato");
        verify(adicionalRepository).save(any(ContratoAdicional.class));
    }

    @Test
    void duplicadoSeIgnora() throws Exception {
        when(procesadoRepository.existsById(any())).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesarServicioAdicional(adicional());

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        verify(adicionalRepository, never()).save(any());
    }

    @Test
    void mensajesInvalidos() throws Exception {
        var sinEventoId = registro("SERVICIO_ADICIONAL_AGREGADO", null, "{}");
        var otroTipo = registro("EVENTO_CONFIRMADO", eventoId, "{}");
        var sinValores = registro("SERVICIO_ADICIONAL_AGREGADO", eventoId,
                "{\"adicionalId\":\"%s\",\"eventoId\":\"%s\",\"codigo\":\"x\",\"nombre\":\"x\",\"cantidad\":1}".formatted(adicionalId, eventoId));

        assertThatThrownBy(() -> service.procesarServicioAdicional(sinEventoId)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesarServicioAdicional(otroTipo)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesarServicioAdicional(sinValores)).isInstanceOf(MensajeInvalidoException.class);
        verify(adicionalRepository, never()).save(any());
    }
}

package com.maxieventos.auditoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.auditoria.entity.AuditoriaEvento;
import com.maxieventos.auditoria.entity.EventoProcesado;
import com.maxieventos.auditoria.kafka.MensajeInvalidoException;
import com.maxieventos.auditoria.kafka.MensajeKafka;
import com.maxieventos.auditoria.kafka.RegistroConsumo;
import com.maxieventos.auditoria.kafka.SobreDeserializer;
import com.maxieventos.auditoria.repository.AuditoriaEventoRepository;
import com.maxieventos.auditoria.repository.EventoProcesadoRepository;
import java.nio.charset.StandardCharsets;
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
class ConsumoAuditoriaServiceTest {

    @Mock
    private AuditoriaEventoRepository auditoriaRepository;
    @Mock
    private EventoProcesadoRepository procesadoRepository;

    private ConsumoAuditoriaService service;
    private final UUID eventoId = UUID.randomUUID();
    private final UUID idEvento = UUID.randomUUID();

    @BeforeEach
    void crear() {
        service = new ConsumoAuditoriaService(auditoriaRepository, procesadoRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private String json(String tipo, UUID eventoDelSobre) {
        return """
                {"idEvento":"%s","tipo":"%s","version":1,"fechaOcurrencia":"2026-10-28T10:00:00Z",
                 "servicioOrigen":"eventos-service","eventoId":%s,"datos":{"nombreEvento":"Quince años de Sofía"}}"""
                .formatted(idEvento, tipo, eventoDelSobre == null ? "null" : "\"" + eventoDelSobre + "\"");
    }

    private ConsumerRecord<String, MensajeKafka> registro(String topico, String json) {
        MensajeKafka mensaje = new SobreDeserializer().deserialize(topico, json.getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>(topico, 2, 41L, eventoId.toString(), mensaje);
    }

    @Test
    void guardaElMensajeConElJsonOriginalYElOrigenKafka() {
        String original = json("EVENTO_CONFIRMADO", eventoId);

        RegistroConsumo.Resultado resultado = service.procesar(registro("eventos.confirmados", original));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<AuditoriaEvento> guardado = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(auditoriaRepository).save(guardado.capture());
        AuditoriaEvento e = guardado.getValue();
        assertThat(e.getIdEvento()).isEqualTo(idEvento);
        assertThat(e.getTipo()).isEqualTo("EVENTO_CONFIRMADO");
        assertThat(e.getEventoId()).isEqualTo(eventoId);
        assertThat(e.getServicioOrigen()).isEqualTo("eventos-service");
        assertThat(e.getPayload()).isEqualTo(original);
        assertThat(e.getTopico()).isEqualTo("eventos.confirmados");
        assertThat(e.getParticion()).isEqualTo(2);
        assertThat(e.getOffsetKafka()).isEqualTo(41L);
        assertThat(e.getFechaOcurrencia()).hasToString("2026-10-28T10:00:00Z");
        assertThat(e.getFechaRecepcion()).isNotNull();
        ArgumentCaptor<EventoProcesado> procesado = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(procesadoRepository).save(procesado.capture());
        assertThat(procesado.getValue().getConsumerGroup()).isEqualTo("auditoria-group");
        assertThat(procesado.getValue().getIdEvento()).isEqualTo(idEvento);
    }

    @Test
    void losTresTiposSeAuditanEnSuTopico() {
        service.procesar(registro("pagos.registrados", json("PAGO_REGISTRADO", eventoId)));
        service.procesar(registro("servicios-adicionales.agregados", json("SERVICIO_ADICIONAL_AGREGADO", eventoId)));

        verify(auditoriaRepository, org.mockito.Mockito.times(2)).save(any(AuditoriaEvento.class));
    }

    /** Fase 11E: el recordatorio EVENTO_PROXIMO se audita en eventos.proximos; en otro tópico es inválido. */
    @Test
    void elRecordatorioEventoProximoSeAuditaEnSuTopico() {
        RegistroConsumo.Resultado resultado = service.procesar(registro("eventos.proximos", json("EVENTO_PROXIMO", eventoId)));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.PROCESADO);
        ArgumentCaptor<AuditoriaEvento> guardado = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(auditoriaRepository).save(guardado.capture());
        assertThat(guardado.getValue().getTipo()).isEqualTo("EVENTO_PROXIMO");
        assertThat(guardado.getValue().getTopico()).isEqualTo("eventos.proximos");
        assertThatThrownBy(() -> service.procesar(registro("eventos.confirmados", json("EVENTO_PROXIMO", eventoId))))
                .isInstanceOf(MensajeInvalidoException.class).hasMessageContaining("se esperaba EVENTO_CONFIRMADO");
    }

    @Test
    void duplicadoSeIgnora() {
        when(procesadoRepository.existsById(any())).thenReturn(true);

        RegistroConsumo.Resultado resultado = service.procesar(registro("eventos.confirmados", json("EVENTO_CONFIRMADO", eventoId)));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void carreraDetectadaPorLaClavePrimariaEsDuplicado() {
        when(procesadoRepository.existsById(any())).thenReturn(false, true);
        when(auditoriaRepository.save(any())).thenThrow(new DataIntegrityViolationException("pk"));

        RegistroConsumo.Resultado resultado = service.procesar(registro("eventos.confirmados", json("EVENTO_CONFIRMADO", eventoId)));

        assertThat(resultado.resultado()).isEqualTo(RegistroConsumo.DUPLICADO_IGNORADO);
        assertThat(resultado.detalle()).contains("detectado por la BD");
    }

    @Test
    void mensajesInvalidosNoSeAuditan() {
        var sinEventoId = registro("eventos.confirmados", json("EVENTO_CONFIRMADO", null));
        var tipoDeOtroTopico = registro("eventos.confirmados", json("PAGO_REGISTRADO", eventoId));
        var tipoDesconocido = registro("pagos.registrados", json("EVENTO_DESCONOCIDO", eventoId));
        var vacio = new ConsumerRecord<String, MensajeKafka>("pagos.registrados", 0, 1L, "k", null);

        assertThatThrownBy(() -> service.procesar(sinEventoId)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesar(tipoDeOtroTopico)).isInstanceOf(MensajeInvalidoException.class)
                .hasMessageContaining("se esperaba EVENTO_CONFIRMADO");
        assertThatThrownBy(() -> service.procesar(tipoDesconocido)).isInstanceOf(MensajeInvalidoException.class);
        assertThatThrownBy(() -> service.procesar(vacio)).isInstanceOf(MensajeInvalidoException.class);
        verify(auditoriaRepository, never()).save(any());
        verify(procesadoRepository, never()).save(any());
    }
}

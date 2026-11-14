package com.maxieventos.eventos.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.eventos.entity.EstadoMensajeSaliente;
import com.maxieventos.eventos.entity.MensajeSaliente;
import com.maxieventos.eventos.repository.MensajeSalienteRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaTest {

    @Mock
    private MensajeSalienteRepository repository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxKafka outbox;
    private final UUID eventoId = UUID.randomUUID();
    private EventoConfirmadoInterno interno;

    @BeforeEach
    void crear() {
        outbox = new OutboxKafka(repository, kafkaTemplate, new ObjectMapper(), mock(PlatformTransactionManager.class),
                Duration.ofMinutes(2));
        interno = new EventoConfirmadoInterno(MensajeKafka.nuevo(DatosEventoConfirmado.TIPO, eventoId,
                new DatosEventoConfirmado(eventoId, "Quince años de Lucía", "QUINCE_ANOS", LocalDate.of(2026, 12, 5),
                        UUID.randomUUID(), null, 90, 15, 105, "NEIVA")));
    }

    private MensajeSaliente fila() throws Exception {
        ArgumentCaptor<MensajeSaliente> guardada = ArgumentCaptor.forClass(MensajeSaliente.class);
        outbox.guardar(interno);
        verify(repository).save(guardada.capture());
        return guardada.getValue();
    }

    private void tomable(MensajeSaliente fila) {
        when(repository.tomar(eq(fila.getIdEvento()), any(), any())).thenReturn(1);
        when(repository.findById(fila.getIdEvento())).thenReturn(Optional.of(fila));
    }

    @SuppressWarnings("unchecked")
    private static SendResult<String, Object> confirmacion() {
        return new SendResult<>(mock(ProducerRecord.class), new RecordMetadata(new TopicPartition("eventos.confirmados", 1), 7, 0, 0, 0, 0));
    }

    @Test
    void guardaLaFilaPendienteConElSobreEnJsonIso() throws Exception {
        MensajeSaliente fila = fila();

        assertThat(fila.getIdEvento()).isEqualTo(interno.mensaje().idEvento());
        assertThat(fila.getEstado()).isEqualTo(EstadoMensajeSaliente.PENDIENTE);
        assertThat(fila.getTopico()).isEqualTo("eventos.confirmados");
        assertThat(fila.getClave()).isEqualTo(eventoId.toString());
        assertThat(fila.getTipo()).isEqualTo("EVENTO_CONFIRMADO");
        JsonNode json = new ObjectMapper().readTree(fila.getPayload());
        assertThat(json.get("idEvento").asText()).isEqualTo(fila.getIdEvento().toString());
        assertThat(json.get("datos").get("fecha").asText()).isEqualTo("2026-12-05");
        assertThat(json.get("fechaOcurrencia").isTextual()).isTrue();
    }

    @Test
    void trasElCommitEnviaConClaveEventoIdYMarcaPublicado() throws Exception {
        MensajeSaliente fila = fila();
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(confirmacion()));

        outbox.enviarTrasCommit(interno);

        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("eventos.confirmados"), eq(eventoId.toString()), valor.capture());
        assertThat(((JsonNode) valor.getValue()).get("idEvento").asText()).isEqualTo(fila.getIdEvento().toString());
        verify(repository).marcarPublicado(eq(fila.getIdEvento()), any(Instant.class));
        verify(repository, never()).devolverAPendiente(any(), any(), any());
    }

    @Test
    void siOtroYaTomoLaFilaNoLaEnvia() throws Exception {
        MensajeSaliente fila = fila();
        when(repository.tomar(eq(fila.getIdEvento()), any(), any())).thenReturn(0);

        assertThat(outbox.enviar(fila.getIdEvento(), OutboxKafka.REENVIO)).isTrue();

        verifyNoInteractions(kafkaTemplate);
        verify(repository, never()).marcarPublicado(any(), any());
    }

    @Test
    void falloAsincronoDeKafkaDevuelveLaFilaAPendiente() throws Exception {
        MensajeSaliente fila = fila();
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Expiring 1 record(s): 30000 ms")));

        assertThat(outbox.enviar(fila.getIdEvento(), OutboxKafka.INMEDIATO)).isTrue();

        verify(repository).devolverAPendiente(eq(fila.getIdEvento()), eq("Expiring 1 record(s): 30000 ms"), any(Instant.class));
        verify(repository, never()).marcarPublicado(any(), any());
    }

    @Test
    void registraLaCausaRaizDelFallo() throws Exception {
        MensajeSaliente fila = fila();
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Send failed", new IllegalStateException("Topic eventos.confirmados not present in metadata after 5000 ms."))));

        outbox.enviar(fila.getIdEvento(), OutboxKafka.INMEDIATO);

        verify(repository).devolverAPendiente(eq(fila.getIdEvento()),
                eq("Topic eventos.confirmados not present in metadata after 5000 ms."), any(Instant.class));
    }

    @Test
    void kafkaSinMetadatosDevuelveFalseYLaFilaQuedaPendiente() throws Exception {
        MensajeSaliente fila = fila();
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("max.block.ms"));

        assertThat(outbox.enviar(fila.getIdEvento(), OutboxKafka.REENVIO)).isFalse();

        verify(repository).devolverAPendiente(eq(fila.getIdEvento()), eq("max.block.ms"), any(Instant.class));
    }

    @Test
    void elReenvioUsaElMismoIdEventoQueElEnvioOriginal() throws Exception {
        MensajeSaliente fila = fila();
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(confirmacion()));

        outbox.enviar(fila.getIdEvento(), OutboxKafka.REENVIO);

        ArgumentCaptor<Object> valor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), anyString(), valor.capture());
        assertThat(((JsonNode) valor.getValue()).get("idEvento").asText()).isEqualTo(interno.mensaje().idEvento().toString());
    }
}

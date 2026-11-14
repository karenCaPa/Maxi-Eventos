package com.maxieventos.finanzas.kafka;

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
import com.maxieventos.finanzas.entity.AdicionalEvento;
import com.maxieventos.finanzas.entity.EstadoMensajeSaliente;
import com.maxieventos.finanzas.entity.MensajeSaliente;
import com.maxieventos.finanzas.entity.Pago;
import com.maxieventos.finanzas.repository.MensajeSalienteRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void crear() {
        outbox = new OutboxKafka(repository, kafkaTemplate, new ObjectMapper(), mock(PlatformTransactionManager.class),
                Duration.ofMinutes(2));
    }

    private MensajeInterno pago() {
        return MensajeInterno.pagoRegistrado(new Pago(eventoId, UUID.randomUUID(), "EFECTIVO", new BigDecimal("1000"), "COP", "clave-1"));
    }

    private MensajeInterno adicional() {
        return MensajeInterno.servicioAdicionalAgregado(new AdicionalEvento(eventoId, "hora-extra",
                "Hora adicional de servicio", 2, new BigDecimal("120000"), new BigDecimal("240000")), "COP");
    }

    private MensajeSaliente guardar(MensajeInterno interno) {
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
    private static SendResult<String, Object> confirmacion(String topico) {
        return new SendResult<>(mock(ProducerRecord.class), new RecordMetadata(new TopicPartition(topico, 0), 3, 0, 0, 0, 0));
    }

    @Test
    void pagoGuardadoEnElOutboxConSuTopicoYSobre() throws Exception {
        MensajeSaliente fila = guardar(pago());

        assertThat(fila.getEstado()).isEqualTo(EstadoMensajeSaliente.PENDIENTE);
        assertThat(fila.getTopico()).isEqualTo("pagos.registrados");
        assertThat(fila.getClave()).isEqualTo(eventoId.toString());
        JsonNode json = mapper.readTree(fila.getPayload());
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "idEvento", "tipo", "version", "fechaOcurrencia", "servicioOrigen", "eventoId", "datos");
        assertThat(json.get("tipo").asText()).isEqualTo("PAGO_REGISTRADO");
        assertThat(json.get("servicioOrigen").asText()).isEqualTo("finanzas-service");
        assertThat(json.get("idEvento").asText()).isEqualTo(fila.getIdEvento().toString());
        assertThat(json.get("datos").get("claveIdempotencia").asText()).isEqualTo("clave-1");
        assertThat(json.get("datos").get("monto").decimalValue()).isEqualByComparingTo("1000");
        assertThat(json.get("datos").get("fechaPago").isTextual()).isTrue();
    }

    @Test
    void adicionalGuardadoEnSuTopico() throws Exception {
        MensajeSaliente fila = guardar(adicional());

        assertThat(fila.getTopico()).isEqualTo("servicios-adicionales.agregados");
        JsonNode datos = mapper.readTree(fila.getPayload()).get("datos");
        assertThat(datos.get("codigo").asText()).isEqualTo("hora-extra");
        assertThat(datos.get("nombre").asText()).isEqualTo("Hora adicional de servicio");
        assertThat(datos.get("cantidad").asInt()).isEqualTo(2);
        assertThat(datos.get("subtotal").decimalValue()).isEqualByComparingTo("240000");
        assertThat(datos.get("moneda").asText()).isEqualTo("COP");
    }

    @Test
    void trasElCommitEnviaAlTopicoConClaveEventoIdYMarcaPublicado() {
        MensajeInterno interno = adicional();
        MensajeSaliente fila = guardar(interno);
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(confirmacion("servicios-adicionales.agregados")));

        outbox.enviarTrasCommit(interno);

        verify(kafkaTemplate).send(eq("servicios-adicionales.agregados"), eq(eventoId.toString()), any(JsonNode.class));
        verify(repository).marcarPublicado(eq(fila.getIdEvento()), any(Instant.class));
    }

    @Test
    void siOtroYaTomoLaFilaNoLaEnvia() {
        MensajeSaliente fila = guardar(pago());
        when(repository.tomar(eq(fila.getIdEvento()), any(), any())).thenReturn(0);

        outbox.enviar(fila.getIdEvento(), OutboxKafka.INMEDIATO);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void falloDeKafkaDevuelveLaFilaAPendiente() {
        MensajeSaliente fila = guardar(pago());
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker caído")));

        outbox.enviar(fila.getIdEvento(), OutboxKafka.REENVIO);

        verify(repository).devolverAPendiente(eq(fila.getIdEvento()), eq("broker caído"), any(Instant.class));
        verify(repository, never()).marcarPublicado(any(), any());
    }

    @Test
    void kafkaSinMetadatosDetieneElReenvio() {
        MensajeSaliente fila = guardar(pago());
        tomable(fila);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("max.block.ms"));

        assertThat(outbox.enviar(fila.getIdEvento(), OutboxKafka.REENVIO)).isFalse();
        verify(repository).devolverAPendiente(eq(fila.getIdEvento()), eq("max.block.ms"), any(Instant.class));
    }
}

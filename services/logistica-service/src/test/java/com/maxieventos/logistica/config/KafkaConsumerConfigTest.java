package com.maxieventos.logistica.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.logistica.kafka.MensajeInvalidoException;
import com.maxieventos.logistica.kafka.MensajeKafka;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

class KafkaConsumerConfigTest {

    @Test
    void mensajesInvalidosEIlegiblesNoSeReintentan() {
        assertThat(KafkaConsumerConfig.esNoReintentable(
                new ListenerExecutionFailedException("listener", new MensajeInvalidoException("sin eventoId")))).isTrue();
        assertThat(KafkaConsumerConfig.esNoReintentable(
                new DeserializationException("JSON ilegible", new byte[] {1}, false, new RuntimeException()))).isTrue();
    }

    /** Spring Kafka 3 usaría "<tópico>-dlt"; el DLT del proyecto (NewTopic declarado) es "<tópico>.DLT". */
    @Test
    void elDltEsTopicoPuntoDltEnLaMismaParticion() {
        var destino = KafkaConsumerConfig.destinoDlt(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "eventos.confirmados", 2, 7L, "k", "v"));

        assertThat(destino.topic()).isEqualTo("eventos.confirmados.DLT");
        assertThat(destino.partition()).isEqualTo(2);
    }

    @Test
    void erroresTransitoriosSiSeReintentan() {
        assertThat(KafkaConsumerConfig.esNoReintentable(new ListenerExecutionFailedException("listener",
                new DataAccessResourceFailureException("BD no disponible")))).isFalse();
    }

    @Test
    void causaRaizDelError() {
        assertThat(KafkaConsumerConfig.causaRaiz(new ListenerExecutionFailedException("listener",
                new RuntimeException("envoltorio", new IllegalStateException("Connection refused")))))
                .isEqualTo("IllegalStateException: Connection refused");
    }

    @Test
    @SuppressWarnings("unchecked")
    void elProductorDelDltReenviaLosBytesIlegiblesTalCualYLoDemasComoJson() throws Exception {
        var plantilla = KafkaConsumerConfig.plantillaDlt("localhost:1", new ObjectMapper());
        Serializer<Object> serializador = (Serializer<Object>) plantilla.getProducerFactory().getValueSerializerSupplier().get();
        byte[] ilegible = "{esto no es json".getBytes(StandardCharsets.UTF_8);

        assertThat(serializador.serialize("eventos.confirmados.DLT", ilegible)).isEqualTo(ilegible);

        MensajeKafka mensaje = new MensajeKafka(UUID.randomUUID(), "TIPO_DESCONOCIDO", 1,
                Instant.parse("2026-10-28T10:00:00Z"), "test", UUID.randomUUID(), new ObjectMapper().createObjectNode());
        JsonNode json = new ObjectMapper().readTree(serializador.serialize("eventos.confirmados.DLT", mensaje));
        assertThat(json.get("tipo").asText()).isEqualTo("TIPO_DESCONOCIDO");
        assertThat(json.get("fechaOcurrencia").asText()).isEqualTo("2026-10-28T10:00:00Z");
    }
}

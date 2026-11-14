package com.maxieventos.notificaciones.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxieventos.notificaciones.kafka.MensajeInvalidoException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.serializer.DeserializationException;

class KafkaConsumerConfigTest {

    /** Spring Kafka 3 usaría "<tópico>-dlt"; el DLT del proyecto (NewTopic declarado) es "<tópico>.DLT". */
    @Test
    void elDltEsTopicoPuntoDltEnLaMismaParticion() {
        var destino = KafkaConsumerConfig.destinoDlt(new ConsumerRecord<>("eventos.confirmados", 2, 3L, "k", "v"));

        assertThat(destino.topic()).isEqualTo("eventos.confirmados.DLT");
        assertThat(destino.partition()).isEqualTo(2);
        assertThat(destino.topic()).doesNotEndWith("-dlt");
    }

    @Test
    void invalidosEIlegiblesNoSeReintentanYLosTransitoriosSi() {
        assertThat(KafkaConsumerConfig.esNoReintentable(
                new ListenerExecutionFailedException("listener", new MensajeInvalidoException("sin eventoId")))).isTrue();
        assertThat(KafkaConsumerConfig.esNoReintentable(
                new DeserializationException("JSON ilegible", new byte[] {1}, false, new RuntimeException()))).isTrue();
        assertThat(KafkaConsumerConfig.esNoReintentable(new ListenerExecutionFailedException("listener",
                new DataAccessResourceFailureException("BD no disponible")))).isFalse();
    }
}

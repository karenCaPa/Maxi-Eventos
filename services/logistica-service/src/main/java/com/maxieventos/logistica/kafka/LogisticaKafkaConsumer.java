package com.maxieventos.logistica.kafka;

import com.maxieventos.logistica.config.KafkaConsumerConfig;
import com.maxieventos.logistica.service.ConsumoLogisticaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidores de Logística (grupo {@code logistica-group}). Cada consumo deja una línea de log con formato fijo
 * ({@link RegistroConsumo}); los errores los maneja el {@code DefaultErrorHandler} (reintentos y DLT).
 */
@Component
public class LogisticaKafkaConsumer {
    private final ConsumoLogisticaService consumo;

    public LogisticaKafkaConsumer(ConsumoLogisticaService consumo) {
        this.consumo = consumo;
    }

    @KafkaListener(topics = KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirEventoConfirmado(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarEventoConfirmado(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }

    @KafkaListener(topics = KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirServicioAdicional(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarServicioAdicional(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }
}

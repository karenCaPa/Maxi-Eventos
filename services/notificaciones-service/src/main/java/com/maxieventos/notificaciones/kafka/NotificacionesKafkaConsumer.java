package com.maxieventos.notificaciones.kafka;

import com.maxieventos.notificaciones.config.KafkaConsumerConfig;
import com.maxieventos.notificaciones.service.ConsumoNotificacionesService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de Notificaciones (grupo {@code notificaciones-group}): genera una notificación simulada por cada evento
 * confirmado y cada pago. Cada consumo deja una línea de log con formato fijo ({@link RegistroConsumo}); los errores los
 * maneja el {@code DefaultErrorHandler}.
 */
@Component
public class NotificacionesKafkaConsumer {
    private final ConsumoNotificacionesService consumo;

    public NotificacionesKafkaConsumer(ConsumoNotificacionesService consumo) {
        this.consumo = consumo;
    }

    @KafkaListener(topics = KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirEventoConfirmado(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarEventoConfirmado(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }

    @KafkaListener(topics = KafkaConsumerConfig.PAGOS_REGISTRADOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirPagoRegistrado(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarPagoRegistrado(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }

    /** Fase 11E: recordatorio de un evento próximo ("Faltan N días para su evento…"). */
    @KafkaListener(topics = KafkaConsumerConfig.EVENTOS_PROXIMOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirEventoProximo(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarEventoProximo(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }
}

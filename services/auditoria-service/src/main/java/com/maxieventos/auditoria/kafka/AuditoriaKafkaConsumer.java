package com.maxieventos.auditoria.kafka;

import com.maxieventos.auditoria.config.KafkaConsumerConfig;
import com.maxieventos.auditoria.service.ConsumoAuditoriaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de Auditoría (grupo {@code auditoria-group}): registra todos los mensajes de los tres tópicos. Cada consumo
 * deja una línea de log con formato fijo ({@link RegistroConsumo}); los errores los maneja el {@code DefaultErrorHandler}.
 */
@Component
public class AuditoriaKafkaConsumer {
    private final ConsumoAuditoriaService consumo;

    public AuditoriaKafkaConsumer(ConsumoAuditoriaService consumo) {
        this.consumo = consumo;
    }

    @KafkaListener(topics = KafkaConsumerConfig.EVENTOS_CONFIRMADOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirEventoConfirmado(ConsumerRecord<String, MensajeKafka> registro) {
        registrar(registro);
    }

    @KafkaListener(topics = KafkaConsumerConfig.PAGOS_REGISTRADOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirPagoRegistrado(ConsumerRecord<String, MensajeKafka> registro) {
        registrar(registro);
    }

    @KafkaListener(topics = KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirServicioAdicional(ConsumerRecord<String, MensajeKafka> registro) {
        registrar(registro);
    }

    /** Fase 11E: recordatorios EVENTO_PROXIMO. */
    @KafkaListener(topics = KafkaConsumerConfig.EVENTOS_PROXIMOS_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirEventoProximo(ConsumerRecord<String, MensajeKafka> registro) {
        registrar(registro);
    }

    private void registrar(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesar(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }
}

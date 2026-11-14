package com.maxieventos.contratos.kafka;

import com.maxieventos.contratos.config.KafkaConsumerConfig;
import com.maxieventos.contratos.service.ConsumoContratosService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de Contratos (grupo {@code contratos-group}): registra los servicios adicionales para el anexo. Cada consumo
 * deja una línea de log con formato fijo ({@link RegistroConsumo}); los errores los maneja el {@code DefaultErrorHandler}.
 */
@Component
public class ContratosKafkaConsumer {
    private final ConsumoContratosService consumo;

    public ContratosKafkaConsumer(ConsumoContratosService consumo) {
        this.consumo = consumo;
    }

    @KafkaListener(topics = KafkaConsumerConfig.SERVICIOS_ADICIONALES_TOPIC, groupId = KafkaConsumerConfig.GRUPO)
    public void consumirServicioAdicional(ConsumerRecord<String, MensajeKafka> registro) {
        RegistroConsumo.Resultado resultado = consumo.procesarServicioAdicional(registro);
        RegistroConsumo.registrar(KafkaConsumerConfig.GRUPO, registro, resultado.resultado(), resultado.detalle());
    }
}

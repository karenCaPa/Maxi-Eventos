package com.maxieventos.eventos.kafka;

/**
 * Evento interno de Spring que lleva un mensaje Kafka y su tópico (Fase 11E: lo implementan EVENTO_CONFIRMADO y
 * EVENTO_PROXIMO). {@link OutboxKafka} lo guarda en el outbox dentro de la transacción que lo publica (BEFORE_COMMIT) y lo
 * envía a Kafka DESPUÉS del commit.
 */
public interface MensajeInterno {

    MensajeKafka<?> mensaje();

    String topico();
}

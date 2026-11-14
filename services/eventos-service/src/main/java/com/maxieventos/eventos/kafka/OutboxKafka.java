package com.maxieventos.eventos.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.eventos.config.KafkaProducerConfig;
import com.maxieventos.eventos.entity.MensajeSaliente;
import com.maxieventos.eventos.repository.MensajeSalienteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox de los mensajes Kafka de eventos-service (entrega "al menos una vez"):
 * <ol>
 *   <li>{@link #guardar}: dentro de la transacción de negocio (BEFORE_COMMIT) se guarda la fila PENDIENTE con el sobre.
 *       Si la transacción se revierte, la fila tampoco existe.</li>
 *   <li>{@link #enviarTrasCommit}: después del commit se intenta publicar enseguida.</li>
 *   <li>{@link ReenvioOutbox}: una tarea periódica reenvía lo que siga PENDIENTE (por ejemplo, si Kafka estaba caído).</li>
 * </ol>
 * Para que el envío inmediato y el reenvío no publiquen la misma fila a la vez, cada uno la <b>toma</b> antes con un
 * UPDATE atómico (PENDIENTE → ENVIANDO); solo quien la tomó la envía. Kafka confirma → PUBLICADO; falla → PENDIENTE.
 */
@Component
public class OutboxKafka {

    public static final String INMEDIATO = "INMEDIATO";
    public static final String REENVIO = "REENVIO";

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxKafka.class);

    private final MensajeSalienteRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaccionNueva;
    private final Duration envioAtascado;

    public OutboxKafka(MensajeSalienteRepository repository, KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper, PlatformTransactionManager transactionManager,
                       @Value("${maxi-eventos.outbox.envio-atascado:PT2M}") Duration envioAtascado) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = KafkaProducerConfig.mapperJson(objectMapper);
        // Los cambios de estado van en su propia transacción: después del commit (o en el hilo de Kafka) no hay otra.
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.envioAtascado = envioAtascado;
    }

    /** Fase 11E: cualquier {@link MensajeInterno} (EVENTO_CONFIRMADO, EVENTO_PROXIMO), cada uno a su tópico. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void guardar(MensajeInterno interno) {
        MensajeKafka<?> mensaje = interno.mensaje();
        try {
            repository.save(new MensajeSaliente(mensaje.idEvento(), mensaje.tipo(), interno.topico(),
                    mensaje.eventoId().toString(), mapper.writeValueAsString(mensaje), Instant.now()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el mensaje " + mensaje.tipo(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarTrasCommit(MensajeInterno interno) {
        enviar(interno.mensaje().idEvento(), INMEDIATO);
    }

    /**
     * Toma la fila y la envía. Devuelve {@code false} solo si Kafka falló de inmediato (sin metadatos: max.block.ms), para
     * que el reenvío no siga intentando el resto del lote; un fallo asíncrono devuelve la fila a PENDIENTE.
     */
    public boolean enviar(UUID idEvento, String origen) {
        Instant ahora = Instant.now();
        Integer tomadas = transaccionNueva.execute(estado -> repository.tomar(idEvento, ahora, ahora.minus(envioAtascado)));
        if (tomadas == null || tomadas == 0) {
            LOGGER.debug("Mensaje del outbox ya tomado por otro envío o ya publicado. idEvento={}, origen={}", idEvento, origen);
            return true;
        }
        MensajeSaliente fila = transaccionNueva.execute(estado -> repository.findById(idEvento).orElseThrow());
        try {
            JsonNode valor = mapper.readTree(fila.getPayload());
            kafkaTemplate.send(fila.getTopico(), fila.getClave(), valor).whenComplete((resultado, error) -> {
                if (error == null) {
                    publicado(fila, resultado, origen);
                } else {
                    fallo(fila, error, origen);
                }
            });
            return true;
        } catch (JsonProcessingException | RuntimeException ex) {
            fallo(fila, ex, origen);
            return false;
        }
    }

    private void publicado(MensajeSaliente fila, SendResult<String, Object> resultado, String origen) {
        transaccionNueva.executeWithoutResult(estado -> repository.marcarPublicado(fila.getIdEvento(), Instant.now()));
        int particion = resultado.getRecordMetadata().partition();
        long offset = resultado.getRecordMetadata().offset();
        if (REENVIO.equals(origen)) {
            LOGGER.info("Reenvío del outbox. idEvento={}, tipo={}, eventoId={}, topic={}, intento={}, resultado=PUBLICADO, "
                    + "partition={}, offset={}", fila.getIdEvento(), fila.getTipo(), fila.getClave(), fila.getTopico(),
                    fila.getIntentos(), particion, offset);
        } else {
            LOGGER.info("{} publicado. idEvento={}, eventoId={}, topic={}, partition={}, offset={}, intento={}", fila.getTipo(),
                    fila.getIdEvento(), fila.getClave(), fila.getTopico(), particion, offset, fila.getIntentos());
        }
    }

    private void fallo(MensajeSaliente fila, Throwable error, String origen) {
        // Causa raíz: el mensaje de Spring ("Send failed") no dice por qué; la de Kafka sí (timeout, sin metadatos...).
        Throwable raiz = error;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String causa = raiz.getMessage() == null ? raiz.getClass().getSimpleName() : raiz.getMessage();
        transaccionNueva.executeWithoutResult(estado -> repository.devolverAPendiente(fila.getIdEvento(),
                causa.length() > 500 ? causa.substring(0, 500) : causa, Instant.now()));
        if (REENVIO.equals(origen)) {
            LOGGER.warn("Reenvío del outbox. idEvento={}, tipo={}, eventoId={}, topic={}, intento={}, resultado=FALLÓ, causa={}",
                    fila.getIdEvento(), fila.getTipo(), fila.getClave(), fila.getTopico(), fila.getIntentos(), causa);
        } else {
            LOGGER.error("FALLÓ la publicación de {} (queda PENDIENTE en el outbox y se reintentará). idEvento={}, eventoId={}, "
                    + "topic={}, intento={}, causa={}", fila.getTipo(), fila.getIdEvento(), fila.getClave(), fila.getTopico(),
                    fila.getIntentos(), causa);
        }
    }
}

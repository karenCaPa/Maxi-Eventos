package com.maxieventos.eventos.kafka;

import com.maxieventos.eventos.repository.MensajeSalienteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reenvía periódicamente los mensajes del outbox que siguen PENDIENTE desde hace más de {@code espera-minima} (el envío
 * inmediato ya falló) o que quedaron ENVIANDO "atascados" (el proceso se detuvo antes de la confirmación de Kafka).
 * Si Kafka no responde, se detiene el lote hasta la próxima ejecución.
 */
@Component
public class ReenvioOutbox {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReenvioOutbox.class);

    private final MensajeSalienteRepository repository;
    private final OutboxKafka outbox;
    private final Duration esperaMinima;
    private final Duration envioAtascado;
    private final int lote;

    public ReenvioOutbox(MensajeSalienteRepository repository, OutboxKafka outbox,
                         @Value("${maxi-eventos.outbox.espera-minima:PT30S}") Duration esperaMinima,
                         @Value("${maxi-eventos.outbox.envio-atascado:PT2M}") Duration envioAtascado,
                         @Value("${maxi-eventos.outbox.lote:50}") int lote) {
        this.repository = repository;
        this.outbox = outbox;
        this.esperaMinima = esperaMinima;
        this.envioAtascado = envioAtascado;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${maxi-eventos.outbox.intervalo-ms:15000}",
            initialDelayString = "${maxi-eventos.outbox.retraso-inicial-ms:20000}")
    public void reenviarPendientes() {
        Instant ahora = Instant.now();
        List<UUID> pendientes = repository.candidatosAReenvio(ahora.minus(esperaMinima), ahora.minus(envioAtascado),
                PageRequest.of(0, lote));
        if (pendientes.isEmpty()) {
            return;
        }
        LOGGER.info("Reenvío del outbox: {} mensaje(s) pendiente(s)", pendientes.size());
        for (UUID idEvento : pendientes) {
            if (!outbox.enviar(idEvento, OutboxKafka.REENVIO)) {
                LOGGER.warn("Kafka no responde: el reenvío del outbox se detiene hasta la próxima ejecución");
                return;
            }
        }
    }
}

package com.maxieventos.eventos.kafka;

import com.maxieventos.eventos.config.KafkaTopicConfig;
import com.maxieventos.eventos.entity.Evento;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Recordatorio de un evento próximo (Fase 11E). Se publica en la misma transacción que guarda la fila de
 * {@code eventos_recordatorios_enviados}; el {@link OutboxKafka} lo guarda en el outbox y lo envía después del commit.
 * <p>
 * El {@code idEvento} es DETERMINISTA (UUID de nombre a partir de evento y umbral): aunque algo saliera mal y se intentara
 * dos veces, el outbox (clave primaria {@code id_evento}) y los consumidores (su tabla de idempotencia) lo descartan.
 */
public record EventoProximoInterno(MensajeKafka<DatosEventoProximo> mensaje) implements MensajeInterno {

    @Override
    public String topico() {
        return KafkaTopicConfig.EVENTOS_PROXIMOS;
    }

    public static UUID idDeterminista(UUID eventoId, int umbralDias) {
        return UUID.nameUUIDFromBytes((DatosEventoProximo.TIPO + ":" + eventoId + ":" + umbralDias).getBytes(StandardCharsets.UTF_8));
    }

    public static EventoProximoInterno de(Evento evento, int diasRestantes, int umbralDias) {
        return new EventoProximoInterno(MensajeKafka.conId(idDeterminista(evento.getId(), umbralDias), DatosEventoProximo.TIPO,
                evento.getId(), new DatosEventoProximo(
                        evento.getId(),
                        evento.getNombreEvento(),
                        evento.getTipoEvento().name(),
                        evento.getFecha(),
                        evento.getClienteId(),
                        evento.getLugarId(),
                        evento.getNumInvitados(),
                        diasRestantes,
                        umbralDias)));
    }
}

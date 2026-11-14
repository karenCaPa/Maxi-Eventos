package com.maxieventos.eventos.kafka;

import com.maxieventos.eventos.config.KafkaTopicConfig;
import com.maxieventos.eventos.entity.Evento;

/**
 * Evento interno de Spring que se publica dentro de la transacción de confirmación; el {@link OutboxKafka} lo guarda
 * en el outbox en esa misma transacción y lo envía a Kafka DESPUÉS del commit.
 */
public record EventoConfirmadoInterno(MensajeKafka<DatosEventoConfirmado> mensaje) implements MensajeInterno {

    @Override
    public String topico() {
        return KafkaTopicConfig.EVENTOS_CONFIRMADOS;
    }

    public static EventoConfirmadoInterno de(Evento evento) {
        return new EventoConfirmadoInterno(MensajeKafka.nuevo(DatosEventoConfirmado.TIPO, evento.getId(),
                new DatosEventoConfirmado(
                        evento.getId(),
                        evento.getNombreEvento(),
                        evento.getTipoEvento().name(),
                        evento.getFecha(),
                        evento.getClienteId(),
                        evento.getLugarId(),
                        evento.getCantidadAdultos(),
                        evento.getCantidadNinos(),
                        evento.getNumInvitados(),
                        evento.getZona().name())));
    }
}

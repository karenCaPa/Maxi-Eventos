package com.maxieventos.finanzas.kafka;

import com.maxieventos.finanzas.config.KafkaTopicConfig;
import com.maxieventos.finanzas.entity.AdicionalEvento;
import com.maxieventos.finanzas.entity.Pago;

/**
 * Evento interno de Spring que se publica dentro de la transacción del pago o del adicional; el {@link OutboxKafka}
 * lo guarda en el outbox en esa misma transacción y lo envía a Kafka DESPUÉS del commit.
 */
public record MensajeInterno(String topico, MensajeKafka<?> mensaje) {

    public static MensajeInterno pagoRegistrado(Pago pago) {
        return new MensajeInterno(KafkaTopicConfig.PAGOS_REGISTRADOS, MensajeKafka.nuevo(DatosPagoRegistrado.TIPO,
                pago.getEventoId(), new DatosPagoRegistrado(pago.getId(), pago.getEventoId(), pago.getClienteId(),
                        pago.getMonto(), pago.getMoneda(), pago.getMetodoPago(), pago.getClaveIdempotencia(),
                        pago.getCreadoEn())));
    }

    public static MensajeInterno servicioAdicionalAgregado(AdicionalEvento adicional, String moneda) {
        return new MensajeInterno(KafkaTopicConfig.SERVICIOS_ADICIONALES_AGREGADOS, MensajeKafka.nuevo(
                DatosServicioAdicionalAgregado.TIPO, adicional.getEventoId(), new DatosServicioAdicionalAgregado(
                        adicional.getId(), adicional.getEventoId(), adicional.getCodigo(), adicional.getNombre(),
                        adicional.getCantidad(), adicional.getValorUnitario(), adicional.getSubtotal(), moneda)));
    }
}

package com.maxieventos.finanzas.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre común de todos los mensajes Kafka de Maxi Eventos (igual en productores y consumidores).
 * <ul>
 *   <li>{@code idEvento}: UUID único del mensaje; los consumidores lo usan para la idempotencia.</li>
 *   <li>{@code tipo}: EVENTO_CONFIRMADO | PAGO_REGISTRADO | SERVICIO_ADICIONAL_AGREGADO.</li>
 *   <li>{@code version}: versión del formato de {@code datos}.</li>
 *   <li>{@code eventoId}: evento de negocio al que se refiere (también es la clave del mensaje).</li>
 * </ul>
 */
public record MensajeKafka<T>(
        UUID idEvento,
        String tipo,
        int version,
        Instant fechaOcurrencia,
        String servicioOrigen,
        UUID eventoId,
        T datos) {

    public static final int VERSION_ACTUAL = 1;
    public static final String SERVICIO_ORIGEN = "finanzas-service";

    public static <T> MensajeKafka<T> nuevo(String tipo, UUID eventoId, T datos) {
        return new MensajeKafka<>(UUID.randomUUID(), tipo, VERSION_ACTUAL, Instant.now(), SERVICIO_ORIGEN, eventoId, datos);
    }
}

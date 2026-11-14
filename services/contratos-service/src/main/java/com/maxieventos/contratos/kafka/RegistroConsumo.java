package com.maxieventos.contratos.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Línea de log fija de cada consumo Kafka (la misma en todos los consumidores, fácil de buscar en las evidencias):
 * <pre>
 * Consumo Kafka. grupo=…, topic=…, particion=…, offset=…, idEvento=…, tipo=…, eventoId=…, resultado=…[, detalle=…]
 * </pre>
 * Resultados: PROCESADO, DUPLICADO_IGNORADO, INVALIDO_A_DLT, REINTENTO n, REINTENTOS_AGOTADOS_A_DLT.
 */
public final class RegistroConsumo {

    public static final String PROCESADO = "PROCESADO";
    public static final String DUPLICADO_IGNORADO = "DUPLICADO_IGNORADO";
    public static final String INVALIDO_A_DLT = "INVALIDO_A_DLT";
    public static final String REINTENTOS_AGOTADOS_A_DLT = "REINTENTOS_AGOTADOS_A_DLT";

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroConsumo.class);

    private RegistroConsumo() {
    }

    /** Resultado de procesar un mensaje válido. */
    public record Resultado(String resultado, String detalle) {
        public static Resultado procesado(String detalle) {
            return new Resultado(PROCESADO, detalle);
        }

        public static Resultado duplicado(String detalle) {
            return new Resultado(DUPLICADO_IGNORADO, detalle);
        }
    }

    public static void registrar(String grupo, ConsumerRecord<?, ?> registro, String resultado, String detalle) {
        String linea = linea(grupo, registro, resultado, detalle);
        if (PROCESADO.equals(resultado) || DUPLICADO_IGNORADO.equals(resultado)) {
            LOGGER.info(linea);
        } else if (resultado.startsWith("REINTENTO ")) {
            LOGGER.warn(linea);
        } else {
            LOGGER.error(linea);
        }
    }

    static String linea(String grupo, ConsumerRecord<?, ?> registro, String resultado, String detalle) {
        MensajeKafka mensaje = registro.value() instanceof MensajeKafka m ? m : null;
        return "Consumo Kafka. grupo=" + grupo
                + ", topic=" + registro.topic()
                + ", particion=" + registro.partition()
                + ", offset=" + registro.offset()
                + ", idEvento=" + (mensaje == null || mensaje.idEvento() == null ? "-" : mensaje.idEvento())
                + ", tipo=" + (mensaje == null || mensaje.tipo() == null ? "-" : mensaje.tipo())
                + ", eventoId=" + (mensaje == null || mensaje.eventoId() == null ? "-" : mensaje.eventoId())
                + ", resultado=" + resultado
                + (detalle == null || detalle.isBlank() ? "" : ", detalle=" + detalle);
    }
}

package com.maxieventos.logistica.kafka;

/**
 * El mensaje no cumple el contrato (sobre incompleto, tipo desconocido, datos inválidos). NO se reintenta: reintentar no
 * lo arregla. El manejador de errores lo envía directamente al tópico {@code <tópico>.DLT}.
 */
public class MensajeInvalidoException extends RuntimeException {

    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }

    public MensajeInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

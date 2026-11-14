package com.maxieventos.logistica.exception;

/**
 * Un servicio externo necesario (otro microservicio, Kafka, etc.) no respondió. Se responde con 503.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}

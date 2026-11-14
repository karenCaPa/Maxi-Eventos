package com.maxieventos.clientes.exception;

/**
 * El recurso solicitado no existe. Se responde con 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}

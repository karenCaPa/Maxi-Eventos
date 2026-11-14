package com.maxieventos.lugares.exception;

/**
 * La operación viola una regla de negocio o entra en conflicto con el estado actual. Se responde con 409.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String message) {
        super(message);
    }
}

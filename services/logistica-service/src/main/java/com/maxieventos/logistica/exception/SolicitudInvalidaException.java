package com.maxieventos.logistica.exception;

/**
 * La solicitud tiene un valor inválido que no se puede expresar con anotaciones de validación. Se responde con 400.
 */
public class SolicitudInvalidaException extends RuntimeException {

    private final String campo;

    public SolicitudInvalidaException(String message) {
        this(null, message);
    }

    public SolicitudInvalidaException(String campo, String message) {
        super(message);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}

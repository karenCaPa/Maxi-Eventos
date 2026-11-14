package com.maxieventos.gateway.exception;

/** El rol del token no permite la operación (Fase 11D). {@link ErroresGraphQl} la traduce a FORBIDDEN (403). */
public class AccesoDenegadoException extends RuntimeException {

    public static final String MENSAJE = "No tienes permiso para realizar esta operación.";

    private final String operacion;

    public AccesoDenegadoException(String operacion) {
        super(MENSAJE);
        this.operacion = operacion;
    }

    public String getOperacion() {
        return operacion;
    }
}

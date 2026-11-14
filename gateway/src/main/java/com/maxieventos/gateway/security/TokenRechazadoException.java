package com.maxieventos.gateway.security;

/** El token falta, venció o no es válido. El mensaje (en español) es el que recibe la organizadora. */
public class TokenRechazadoException extends RuntimeException {

    public static final String FALTA =
            "Falta el token. Inicia sesión con la mutation login y envía el header Authorization: Bearer <token>.";
    public static final String VENCIDO = "El token venció. Inicia sesión de nuevo con la mutation login.";
    public static final String INVALIDO = "El token no es válido. Inicia sesión de nuevo con la mutation login.";

    /** Motivo corto para el log (nunca se registra el token). */
    private final String motivo;

    public TokenRechazadoException(String mensaje, String motivo) {
        super(mensaje);
        this.motivo = motivo;
    }

    public String getMotivo() {
        return motivo;
    }
}

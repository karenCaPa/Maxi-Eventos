package com.maxieventos.gateway.security;

import com.maxieventos.gateway.exception.AccesoDenegadoException;

/**
 * Autorización por rol en el gateway (Fase 11D, primer nivel de la defensa en profundidad: los servicios vuelven a
 * comprobarlo). El rol sale del claim {@code rol} del token, que {@code TokenInterceptor} copia al {@code GraphQLContext}.
 * Se comprueba ANTES de llamar a ningún servicio. Un token sin rol (emitido antes de la 11D) no es administrador.
 */
public final class Permisos {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String ASISTENTE = "ASISTENTE";

    private Permisos() {
    }

    public static boolean esAdministrador(String rol) {
        return ADMINISTRADOR.equals(rol);
    }

    /** @throws AccesoDenegadoException (FORBIDDEN) si el rol no es ADMINISTRADOR */
    public static void exigirAdministrador(String rol, String operacion) {
        if (!esAdministrador(rol)) {
            throw new AccesoDenegadoException(operacion);
        }
    }
}

package com.maxieventos.proveedores.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.proveedores.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

/**
 * Respuestas de Spring Security con el {@link ErrorResponse} común (idéntico en todos los microservicios):
 * 401 sin token o con token inválido/vencido, 403 sin permiso y 400 para las rutas que rechaza el firewall
 * (por ejemplo un id vacío: {@code /eventos//contrato}). Se escribe el cuerpo directamente, sin {@code sendError}:
 * {@code sendError} pasa por {@code /error} y la respuesta acababa en un 401 sin cuerpo.
 */
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RespuestasSeguridad.class);
    static final String SIN_SESION = "Debes iniciar sesión para realizar esta operación. "
            + "Envía el header Authorization: Bearer <token>.";
    static final String TOKEN_VENCIDO = "El token venció. Inicia sesión de nuevo.";
    static final String TOKEN_INVALIDO = "El token no es válido. Inicia sesión de nuevo.";
    static final String SIN_PERMISO = "No tienes permiso para realizar esta operación.";
    static final String RUTA_INVALIDA =
            "La ruta no es válida: falta un identificador o contiene caracteres no permitidos.";

    private final ObjectMapper objectMapper;

    public RespuestasSeguridad(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escribir(request, response, HttpStatus.UNAUTHORIZED, SIN_SESION);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, SIN_PERMISO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException ex)
            throws IOException {
        LOGGER.warn("Ruta rechazada por el firewall de Spring Security: {}", ex.getMessage());
        escribir(request, response, HttpStatus.BAD_REQUEST, RUTA_INVALIDA);
    }

    void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String mensaje)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.de(status, mensaje, request.getRequestURI(), null));
    }
}

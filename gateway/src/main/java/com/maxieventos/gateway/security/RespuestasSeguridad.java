package com.maxieventos.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Respuestas HTTP de Spring Security en el gateway (todo lo que NO es {@code /graphql}, por ejemplo el actuator) con el
 * mismo formato que el {@code ErrorResponse} de los servicios: {@code timestamp, status, error, message, path}.
 * Los errores de {@code /graphql} son errores GraphQL ({@code UNAUTHORIZED}), ver {@code TokenInterceptor}.
 */
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RespuestasSeguridad.class);
    static final String SIN_SESION = "Debes iniciar sesión para realizar esta operación. "
            + "Envía el header Authorization: Bearer <token>.";
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

    /** Escribe el error directamente (sin sendError, que pasaría por /error y acabaría en un 401 sin cuerpo). */
    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String mensaje)
            throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("message", mensaje);
        cuerpo.put("path", request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}

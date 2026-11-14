package com.maxieventos.gateway.exception;

import com.maxieventos.gateway.controller.DocumentosController;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de las descargas de PDF ({@link DocumentosController}) con el mismo formato que el {@code ErrorResponse} de los
 * servicios: {@code timestamp, status, error, message, path}. Conserva el código y el mensaje en español de Contratos
 * (404 sin contrato, 409 anexo sin adicionales); un servicio sin respuesta o con 502/503/504 → 503; otro 5xx → 502.
 */
@RestControllerAdvice(assignableTypes = DocumentosController.class)
public class ErroresDocumentos {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErroresDocumentos.class);

    @ExceptionHandler(ServicioRemotoException.class)
    public ResponseEntity<Map<String, Object>> servicioRemoto(ServicioRemotoException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getStatus()) {
            case 400, 401, 403, 404, 409 -> HttpStatus.valueOf(ex.getStatus());
            case 502, 503, 504 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> ex.getStatus() >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.valueOf(ex.getStatus());
        };
        if (status.is5xxServerError()) {
            LOGGER.warn("Descarga {} -> {} ({}): {}", request.getRequestURI(), ex.getServicio(), ex.getStatus(), ex.getMessage());
        }
        return respuesta(status, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> idInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respuesta(HttpStatus.BAD_REQUEST, "El identificador del evento no es un UUID válido.", request);
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje, HttpServletRequest request) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("message", mensaje);
        cuerpo.put("path", request.getRequestURI());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }
}

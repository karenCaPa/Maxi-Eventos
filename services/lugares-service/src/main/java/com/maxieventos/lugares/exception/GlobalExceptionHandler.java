package com.maxieventos.lugares.exception;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manejo global de errores, idéntico en todos los microservicios de Maxi Eventos.
 * Todas las respuestas de error usan {@link ErrorResponse}; los mensajes están en español y nunca incluyen
 * trazas ni detalles internos (esos solo se registran en el log).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MENSAJE_VALIDACION = "Los datos enviados no son válidos. Revisa los campos indicados.";

    private static final Map<Integer, String> MENSAJES_POR_ESTADO = Map.of(
            400, "La solicitud no es válida.",
            401, "Debes iniciar sesión para realizar esta operación.",
            403, "No tienes permiso para realizar esta operación.",
            404, "El recurso solicitado no existe.",
            405, "Este método HTTP no está permitido para esta ruta.",
            409, "La operación entra en conflicto con el estado actual de los datos.",
            415, "El tipo de contenido no es compatible. Envía los datos en formato JSON.",
            503, "Un servicio necesario no está disponible en este momento. Intenta de nuevo más tarde.",
            500, "Ocurrió un error interno. Intenta de nuevo más tarde.");

    // ------------------------------------------------------------------ 400: validación y formato

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.add(new ErrorCampo(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errores.add(new ErrorCampo(e.getObjectName(), e.getDefaultMessage())));
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, ruta(request), errores);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getParameterValidationResults().forEach(resultado -> {
            String parametro = resultado.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : resultado.getResolvableErrors()) {
                String campo = error instanceof FieldError fe ? parametro + "." + fe.getField() : parametro;
                errores.add(new ErrorCampo(campo, error.getDefaultMessage()));
            }
        });
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, ruta(request), errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> manejarConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(ultimoNodo(v), v.getMessage()))
                .toList();
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, request.getRequestURI(), errores);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        MismatchedInputException formato = buscarCausa(ex, MismatchedInputException.class);
        if (formato != null && !formato.getPath().isEmpty()) {
            String campo = formato.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
            return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, ruta(request),
                    List.of(new ErrorCampo(campo, mensajeTipo(formato.getTargetType()))));
        }
        return construir(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON válido o está vacío.", ruta(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String campo = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, ruta(request),
                List.of(new ErrorCampo(campo, mensajeTipo(ex.getRequiredType()))));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, ruta(request),
                List.of(new ErrorCampo(ex.getParameterName(), "es obligatorio")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> manejarCabeceraFaltante(MissingRequestHeaderException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_VALIDACION, request.getRequestURI(),
                List.of(new ErrorCampo(ex.getHeaderName(), "la cabecera es obligatoria")));
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Object> manejarSolicitudInvalida(SolicitudInvalidaException ex, HttpServletRequest request) {
        List<ErrorCampo> errores = ex.getCampo() == null ? List.of() : List.of(new ErrorCampo(ex.getCampo(), ex.getMessage()));
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), errores);
    }

    // ------------------------------------------------------------------ 404, 409, 503: negocio e integraciones

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Object> manejarNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<Object> manejarReglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> manejarIntegridad(DataIntegrityViolationException ex, HttpServletRequest request) {
        LOGGER.warn("Conflicto de integridad de datos en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getClass().getSimpleName());
        return construir(HttpStatus.CONFLICT,
                "Ya existe un registro con esos datos o la operación afecta datos relacionados.",
                request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<Object> manejarServicioNoDisponible(ServicioNoDisponibleException ex, HttpServletRequest request) {
        LOGGER.warn("Servicio externo no disponible en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return construir(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI(), List.of());
    }

    // ------------------------------------------------------------------ resto de excepciones de Spring MVC

    /**
     * Punto común de las excepciones estándar de Spring MVC (incluida {@link ResponseStatusException}, cuyo
     * estado se respeta): ruta inexistente, método no permitido, tipo de contenido no soportado, etc.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String mensaje;
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            mensaje = rse.getReason();
        } else if (ex instanceof NoResourceFoundException) {
            mensaje = "La ruta solicitada no existe.";
        } else {
            mensaje = mensajePorEstado(statusCode);
        }
        if (statusCode.is5xxServerError()) {
            LOGGER.error("Error {} en {}", statusCode.value(), ruta(request), ex);
        }
        ErrorResponse cuerpo = ErrorResponse.de(statusCode, mensaje, ruta(request), List.of());
        return new ResponseEntity<>(cuerpo, headers, statusCode);
    }

    // ------------------------------------------------------------------ 500

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> manejarGenerico(Exception ex, HttpServletRequest request) {
        LOGGER.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, mensajePorEstado(HttpStatus.INTERNAL_SERVER_ERROR),
                request.getRequestURI(), List.of());
    }

    // ------------------------------------------------------------------ utilidades

    private static ResponseEntity<Object> construir(HttpStatus status, String mensaje, String ruta,
            List<ErrorCampo> errores) {
        return ResponseEntity.status(status).body(ErrorResponse.de(status, mensaje, ruta, errores));
    }

    private static String mensajePorEstado(HttpStatusCode status) {
        return MENSAJES_POR_ESTADO.getOrDefault(status.value(),
                status.is5xxServerError() ? MENSAJES_POR_ESTADO.get(500) : MENSAJES_POR_ESTADO.get(400));
    }

    private static String ruta(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }

    private static String ultimoNodo(ConstraintViolation<?> violacion) {
        String nombre = null;
        for (Path.Node nodo : violacion.getPropertyPath()) {
            nombre = nodo.getName();
        }
        return nombre;
    }

    /** Mensaje entendible según el tipo de dato que se esperaba. */
    private static String mensajeTipo(Class<?> tipo) {
        if (tipo == null) {
            return "tiene un formato inválido";
        }
        if (tipo.isEnum()) {
            String valores = Arrays.stream(tipo.getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "valor no permitido; los valores válidos son: " + valores;
        }
        if (UUID.class.equals(tipo)) {
            return "debe ser un identificador válido (UUID)";
        }
        if (LocalDate.class.equals(tipo)) {
            return "debe ser una fecha con formato AAAA-MM-DD";
        }
        if (Integer.class.equals(tipo) || int.class.equals(tipo) || Long.class.equals(tipo) || long.class.equals(tipo)) {
            return "debe ser un número entero";
        }
        if (BigDecimal.class.equals(tipo) || Double.class.equals(tipo) || double.class.equals(tipo)) {
            return "debe ser un número";
        }
        if (Boolean.class.equals(tipo) || boolean.class.equals(tipo)) {
            return "debe ser true o false";
        }
        return "tiene un formato inválido";
    }

    private static <T extends Throwable> T buscarCausa(Throwable ex, Class<T> tipo) {
        Throwable actual = ex;
        while (actual != null) {
            if (tipo.isInstance(actual)) {
                return tipo.cast(actual);
            }
            actual = actual.getCause();
        }
        return null;
    }
}

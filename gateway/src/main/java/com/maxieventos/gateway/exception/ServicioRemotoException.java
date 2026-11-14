package com.maxieventos.gateway.exception;

import graphql.ErrorClassification;
import java.util.List;
import org.springframework.graphql.execution.ErrorType;

/**
 * Error que devolvió (o no pudo devolver) un servicio REST. Conserva el código HTTP, el servicio, el mensaje en español
 * del servicio y el detalle por campo; {@link ErroresGraphQl} lo convierte en un error GraphQL.
 */
public class ServicioRemotoException extends RuntimeException {
    private final String servicio;
    private final int status;
    private final List<ErrorCampo> errores;

    public ServicioRemotoException(String servicio, int status, String mensaje, List<ErrorCampo> errores, Throwable causa) {
        super(mensaje, causa);
        this.servicio = servicio;
        this.status = status;
        this.errores = errores == null ? List.of() : List.copyOf(errores);
    }

    /** El servicio no respondió (caído, sin conexión o timeout). */
    public static ServicioRemotoException noDisponible(String servicio, Throwable causa) {
        return new ServicioRemotoException(servicio, 503, "El servicio de " + servicio
                + " no está disponible en este momento. Intenta de nuevo más tarde.", List.of(), causa);
    }

    /** Respuesta de error del servicio; si no trae mensaje, se usa uno neutro según el código. */
    public static ServicioRemotoException deRespuesta(String servicio, int status, ErrorRemoto cuerpo) {
        // El 401 de Spring Security llega sin cuerpo (falta el token o no es válido); el de Auth trae su mensaje (login fallido).
        String mensaje = cuerpo != null && cuerpo.message() != null && !cuerpo.message().isBlank() ? cuerpo.message()
                : mensajePorDefecto(servicio, status);
        return new ServicioRemotoException(servicio, status, mensaje, cuerpo == null ? List.of() : cuerpo.errores(), null);
    }

    private static String mensajePorDefecto(String servicio, int status) {
        return switch (status) {
            case 400 -> "La solicitud al servicio de " + servicio + " no es válida.";
            case 401 -> "Falta el token o no es válido. Inicia sesión con la mutation login y envía el header "
                    + "Authorization: Bearer <token>.";
            case 403 -> "No tienes permiso para esta operación.";
            case 404 -> "El servicio de " + servicio + " no encontró el recurso solicitado.";
            case 409 -> "La operación no se puede realizar en el estado actual (servicio de " + servicio + ").";
            case 502, 503, 504 -> "El servicio de " + servicio + " no está disponible en este momento. Intenta de nuevo más tarde.";
            default -> "El servicio de " + servicio + " tuvo un error al atender la solicitud.";
        };
    }

    public ErrorClassification clasificacion() {
        return switch (status) {
            case 400, 422 -> ErrorType.BAD_REQUEST;
            case 401 -> ErrorType.UNAUTHORIZED;
            case 403 -> ErrorType.FORBIDDEN;
            case 404 -> ErrorType.NOT_FOUND;
            case 409 -> ClasificacionError.CONFLICT;
            case 502, 503, 504 -> ClasificacionError.SERVICE_UNAVAILABLE;
            default -> ErrorType.INTERNAL_ERROR;
        };
    }

    public String getServicio() { return servicio; }
    public int getStatus() { return status; }
    public List<ErrorCampo> getErrores() { return errores; }
}

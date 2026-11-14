package com.maxieventos.lugares.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Formato único de error de la API, idéntico en todos los microservicios de Maxi Eventos.
 * {@code errores} solo aparece cuando hay detalles por campo (por ejemplo, en errores de validación).
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ErrorCampo> errores) {

    public static ErrorResponse de(HttpStatusCode status, String message, String path, List<ErrorCampo> errores) {
        HttpStatus conocido = HttpStatus.resolve(status.value());
        String error = conocido != null ? conocido.getReasonPhrase() : String.valueOf(status.value());
        return new ErrorResponse(Instant.now(), status.value(), error, message, path,
                errores == null ? List.of() : List.copyOf(errores));
    }
}

package com.maxieventos.gateway.exception;

import java.util.List;

/** Cuerpo de error común de los servicios ({@code ErrorResponse}); los campos que no se usan se ignoran. */
public record ErrorRemoto(int status, String error, String message, String path, List<ErrorCampo> errores) {
}

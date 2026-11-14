package com.maxieventos.clientes.exception;

/**
 * Detalle de un error asociado a un campo de la solicitud.
 */
public record ErrorCampo(String campo, String mensaje) {
}

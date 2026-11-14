package com.maxieventos.gateway.exception;

import graphql.ErrorClassification;

/**
 * Clasificaciones que graphql-java y Spring no traen ({@code ErrorType} tiene BAD_REQUEST, UNAUTHORIZED, FORBIDDEN,
 * NOT_FOUND e INTERNAL_ERROR): 409 de negocio y servicio no disponible (503, caído o sin respuesta a tiempo).
 */
public enum ClasificacionError implements ErrorClassification {
    CONFLICT,
    SERVICE_UNAVAILABLE
}

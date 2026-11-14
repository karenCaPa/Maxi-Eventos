package com.maxieventos.gateway.dto;

/** PDF que genera contratos-service (contrato o anexo), tal como llega; el gateway solo lo reenvía. */
public record Documento(byte[] contenido, String nombreArchivo) {
}

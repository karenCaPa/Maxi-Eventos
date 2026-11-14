package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de auditoria-service. El servicio devuelve el payload como objeto JSON; GraphQL lo expone como texto JSON. */
public record RegistroAuditoria(UUID idEvento, String tipo, UUID eventoId, String servicioOrigen,
                                @JsonProperty("payload") JsonNode payloadJson, String topico, int particion, long offset,
                                OffsetDateTime fechaOcurrencia, OffsetDateTime fechaRecepcion) {

    /** Campo GraphQL {@code payload}: el sobre en JSON. */
    public String payload() {
        return payloadJson == null ? null : payloadJson.toString();
    }
}

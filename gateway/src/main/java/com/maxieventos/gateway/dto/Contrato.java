package com.maxieventos.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contrato de contratos-service. {@code fechaFirma} = {@code firmadoEn} del DTO. */
public record Contrato(UUID id, UUID eventoId, String nombreEvento, String estado, OffsetDateTime creadoEn,
                       String urlDocumento, @JsonProperty("firmadoEn") OffsetDateTime fechaFirma, String clienteNombre,
                       LocalDate fechaEvento, String tipoEvento, Integer cantidadAdultos, Integer cantidadNinos,
                       String lugarNombre, BigDecimal valorTotal, String moneda) {
}

package com.maxieventos.contratos.dto;

import com.maxieventos.contratos.entity.Contrato;
import com.maxieventos.contratos.entity.EstadoContrato;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato con la foto de los datos del evento (campos nulos en contratos anteriores a la Fase 4). */
public record ContratoResponseDTO(UUID id, UUID eventoId, String nombreEvento, EstadoContrato estado,
                                 Instant creadoEn, String urlDocumento, Instant firmadoEn,
                                 String clienteNombre, LocalDate fechaEvento, String tipoEvento,
                                 Integer cantidadAdultos, Integer cantidadNinos, String lugarNombre,
                                 BigDecimal valorTotal, String moneda) {
    public static ContratoResponseDTO from(Contrato contrato) {
        return new ContratoResponseDTO(
                contrato.getId(),
                contrato.getEventoId(),
                contrato.getNombreEvento(),
                contrato.getEstado(),
                contrato.getCreadoEn(),
                "/api/v1/eventos/" + contrato.getEventoId() + "/contrato/pdf",
                contrato.getFirmadoEn(),
                contrato.getClienteNombre(),
                contrato.getFechaEvento(),
                contrato.getTipoEvento(),
                contrato.getCantidadAdultos(),
                contrato.getCantidadNinos(),
                contrato.getLugarNombre(),
                contrato.getValorTotal(),
                contrato.getMoneda()
        );
    }
}

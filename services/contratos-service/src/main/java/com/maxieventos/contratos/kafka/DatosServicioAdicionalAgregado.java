package com.maxieventos.contratos.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;

/** Lo que Contratos usa de {@code datos} de SERVICIO_ADICIONAL_AGREGADO (los campos que no conoce se ignoran). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosServicioAdicionalAgregado(UUID adicionalId, UUID eventoId, String codigo, String nombre,
                                             Integer cantidad, BigDecimal valorUnitario, BigDecimal subtotal,
                                             String moneda) {

    public static final String TIPO = "SERVICIO_ADICIONAL_AGREGADO";
}

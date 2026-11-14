package com.maxieventos.logistica.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Lo que Logística usa de {@code datos} de SERVICIO_ADICIONAL_AGREGADO (los campos que no conoce se ignoran). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatosServicioAdicionalAgregado(UUID adicionalId, UUID eventoId, String codigo, String nombre, Integer cantidad) {

    public static final String TIPO = "SERVICIO_ADICIONAL_AGREGADO";
}

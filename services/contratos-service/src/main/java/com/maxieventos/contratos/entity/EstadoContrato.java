package com.maxieventos.contratos.entity;

/** Un contrato nace PENDIENTE y pasa a FIRMADO una sola vez (eventos-service exige FIRMADO para confirmar). */
public enum EstadoContrato {
    PENDIENTE,
    FIRMADO
}

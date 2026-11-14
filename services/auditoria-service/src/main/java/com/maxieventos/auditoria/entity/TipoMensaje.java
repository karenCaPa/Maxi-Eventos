package com.maxieventos.auditoria.entity;

/** Tipos de mensaje del sobre común (campo {@code tipo}) y el tópico en el que se publica cada uno. */
public enum TipoMensaje {
    EVENTO_CONFIRMADO("eventos.confirmados"),
    PAGO_REGISTRADO("pagos.registrados"),
    SERVICIO_ADICIONAL_AGREGADO("servicios-adicionales.agregados"),
    /** Fase 11E: recordatorio de un evento próximo (eventos-service). */
    EVENTO_PROXIMO("eventos.proximos");

    private final String topico;

    TipoMensaje(String topico) {
        this.topico = topico;
    }

    public String topico() {
        return topico;
    }

    public static TipoMensaje deTopico(String topico) {
        for (TipoMensaje tipo : values()) {
            if (tipo.topico.equals(topico)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tópico sin tipo de mensaje asociado: " + topico);
    }
}

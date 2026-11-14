package com.maxieventos.logistica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Servicio adicional recibido por Logística (SERVICIO_ADICIONAL_AGREGADO) con su efecto ya calculado. Se guarda aunque el
 * evento aún no tenga logística: en ese caso se aplica al consumir EVENTO_CONFIRMADO (el orden de llegada no importa).
 */
@Entity
@Table(name = "logistica_adicionales")
public class AdicionalLogistica {
    @Id
    @Column(name = "adicional_id")
    private UUID adicionalId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "meseros_extra", nullable = false)
    private int meserosExtra;

    @Column(name = "cocina_extra", nullable = false)
    private int cocinaExtra;

    @Column(name = "camiones_extra", nullable = false)
    private int camionesExtra;

    /** true cuando ya se sumó a la logística del evento. */
    @Column(nullable = false)
    private boolean aplicado;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;

    protected AdicionalLogistica() {
    }

    public AdicionalLogistica(UUID adicionalId, UUID eventoId, String codigo, int cantidad, int meserosExtra,
                              int cocinaExtra, int camionesExtra, Instant recibidoEn) {
        this.adicionalId = adicionalId;
        this.eventoId = eventoId;
        this.codigo = codigo;
        this.cantidad = cantidad;
        this.meserosExtra = meserosExtra;
        this.cocinaExtra = cocinaExtra;
        this.camionesExtra = camionesExtra;
        this.recibidoEn = recibidoEn;
    }

    /** Suma el efecto a la logística del evento (una sola vez). */
    public void aplicarA(LogisticaEvento logistica) {
        if (!aplicado) {
            logistica.sumar(meserosExtra, cocinaExtra, camionesExtra);
            aplicado = true;
        }
    }

    public UUID getAdicionalId() { return adicionalId; }
    public UUID getEventoId() { return eventoId; }
    public String getCodigo() { return codigo; }
    public int getCamionesExtra() { return camionesExtra; }
    public boolean isAplicado() { return aplicado; }
}

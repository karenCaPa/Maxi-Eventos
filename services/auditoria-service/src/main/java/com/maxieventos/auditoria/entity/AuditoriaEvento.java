package com.maxieventos.auditoria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Registro de auditoría de un mensaje Kafka válido: el sobre, su JSON original y de dónde se leyó (tópico/partición/offset). */
@Entity
@Table(name = "auditoria_eventos")
public class AuditoriaEvento {
    @Id
    @Column(name = "id_evento")
    private UUID idEvento;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "servicio_origen", length = 100)
    private String servicioOrigen;

    /** JSON original tal como llegó del tópico. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 100)
    private String topico;

    @Column(nullable = false)
    private int particion;

    @Column(name = "offset_kafka", nullable = false)
    private long offsetKafka;

    @Column(name = "fecha_ocurrencia")
    private Instant fechaOcurrencia;

    @Column(name = "fecha_recepcion", nullable = false)
    private Instant fechaRecepcion;

    protected AuditoriaEvento() {
    }

    public AuditoriaEvento(UUID idEvento, String tipo, UUID eventoId, String servicioOrigen, String payload, String topico,
                           int particion, long offsetKafka, Instant fechaOcurrencia, Instant fechaRecepcion) {
        this.idEvento = idEvento;
        this.tipo = tipo;
        this.eventoId = eventoId;
        this.servicioOrigen = servicioOrigen;
        this.payload = payload;
        this.topico = topico;
        this.particion = particion;
        this.offsetKafka = offsetKafka;
        this.fechaOcurrencia = fechaOcurrencia;
        this.fechaRecepcion = fechaRecepcion;
    }

    public UUID getIdEvento() { return idEvento; }
    public String getTipo() { return tipo; }
    public UUID getEventoId() { return eventoId; }
    public String getServicioOrigen() { return servicioOrigen; }
    public String getPayload() { return payload; }
    public String getTopico() { return topico; }
    public int getParticion() { return particion; }
    public long getOffsetKafka() { return offsetKafka; }
    public Instant getFechaOcurrencia() { return fechaOcurrencia; }
    public Instant getFechaRecepcion() { return fechaRecepcion; }
}

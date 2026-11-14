package com.maxieventos.notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Notificación simulada generada a partir de un mensaje Kafka (una por {@code idEvento}). */
@Entity
@Table(name = "notificaciones")
public class Notificacion {
    @Id
    private UUID id;

    /** idEvento del sobre que la originó. */
    @Column(name = "id_evento", nullable = false, unique = true)
    private UUID idEvento;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    /** {@code cliente:<clienteId>} (sin datos de contacto hasta la Fase 7). */
    @Column(nullable = false, length = 100)
    private String destinatario;

    @Column(nullable = false)
    private String asunto;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    protected Notificacion() {
    }

    public Notificacion(UUID idEvento, UUID eventoId, String tipo, String destinatario, String asunto, String mensaje,
                        Instant fechaCreacion) {
        this.id = UUID.randomUUID();
        this.idEvento = idEvento;
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.destinatario = destinatario;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.estado = EstadoNotificacion.GENERADA;
        this.fechaCreacion = fechaCreacion;
    }

    public UUID getId() { return id; }
    public UUID getIdEvento() { return idEvento; }
    public UUID getEventoId() { return eventoId; }
    public String getTipo() { return tipo; }
    public String getDestinatario() { return destinatario; }
    public String getAsunto() { return asunto; }
    public String getMensaje() { return mensaje; }
    public EstadoNotificacion getEstado() { return estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}

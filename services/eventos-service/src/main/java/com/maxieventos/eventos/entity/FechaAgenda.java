package com.maxieventos.eventos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Estado de una fecha de la agenda. Solo se guardan las fechas que alguna vez dejaron de estar disponibles;
 * una fecha sin registro se considera DISPONIBLE.
 */
@Entity
@Table(name = "fechas_evento")
public class FechaAgenda {
    @Id
    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoFecha estado = EstadoFecha.DISPONIBLE;

    /** Evento que ocupa la fecha; nulo si está libre o tiene un bloqueo manual. */
    @Column(name = "evento_id")
    private UUID eventoId;

    protected FechaAgenda() {
    }

    public FechaAgenda(LocalDate fecha) {
        this.fecha = fecha;
    }

    public boolean estaLibre() {
        return estado == EstadoFecha.DISPONIBLE;
    }

    public void asignar(EstadoFecha estado, UUID eventoId) {
        this.estado = estado;
        this.eventoId = eventoId;
    }

    public void liberar() {
        this.estado = EstadoFecha.DISPONIBLE;
        this.eventoId = null;
    }

    public LocalDate getFecha() { return fecha; }
    public EstadoFecha getEstado() { return estado; }
    public UUID getEventoId() { return eventoId; }
}

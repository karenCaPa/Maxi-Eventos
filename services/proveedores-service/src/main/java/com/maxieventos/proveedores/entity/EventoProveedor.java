package com.maxieventos.proveedores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "evento_proveedores", uniqueConstraints =
        @UniqueConstraint(name = "uk_evento_proveedor_servicio",
                columnNames = {"evento_id", "proveedor_id", "servicio_asignado"}))
public class EventoProveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventoId;

    @Column(nullable = false)
    private UUID proveedorId;

    @Column(nullable = false)
    private String servicioAsignado;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal costoAcordado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAsignacion estado = EstadoAsignacion.PENDIENTE;

    protected EventoProveedor() {
    }

    public EventoProveedor(UUID eventoId, UUID proveedorId, String servicioAsignado, BigDecimal costoAcordado,
                           EstadoAsignacion estado) {
        this.eventoId = eventoId;
        this.proveedorId = proveedorId;
        this.servicioAsignado = servicioAsignado;
        this.costoAcordado = costoAcordado;
        this.estado = estado;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public UUID getProveedorId() { return proveedorId; }
    public String getServicioAsignado() { return servicioAsignado; }
    public BigDecimal getCostoAcordado() { return costoAcordado; }
    public EstadoAsignacion getEstado() { return estado; }

    public void cambiarEstado(EstadoAsignacion nuevo) {
        this.estado = nuevo;
    }
}

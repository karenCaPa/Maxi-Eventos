package com.maxieventos.eventos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "eventos")
public class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre_evento", nullable = false, length = 150)
    private String nombreEvento;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private TipoEvento tipoEvento;

    @Column(name = "cantidad_adultos", nullable = false)
    private int cantidadAdultos;

    @Column(name = "cantidad_ninos", nullable = false)
    private int cantidadNinos;

    @Column(name = "num_invitados", nullable = false)
    private int numInvitados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ZonaEvento zona;

    @Column(name = "lugar_id")
    private UUID lugarId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvento estado = EstadoEvento.PENDIENTE;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion = Instant.now();

    protected Evento() {
    }

    public Evento(String nombreEvento, LocalDate fecha, UUID clienteId, TipoEvento tipoEvento, int cantidadAdultos,
                  int cantidadNinos, ZonaEvento zona, UUID lugarId) {
        this.nombreEvento = nombreEvento;
        this.fecha = fecha;
        this.clienteId = clienteId;
        this.tipoEvento = tipoEvento;
        this.cantidadAdultos = cantidadAdultos;
        this.cantidadNinos = cantidadNinos;
        this.numInvitados = cantidadAdultos + cantidadNinos;
        this.zona = zona;
        this.lugarId = lugarId;
    }

    public void confirmar() { this.estado = EstadoEvento.RESERVADO; }
    public void cancelar() { this.estado = EstadoEvento.CANCELADO; }
    public void asignarLugar(UUID lugarId) { this.lugarId = lugarId; }

    public UUID getId() { return id; }
    public String getNombreEvento() { return nombreEvento; }
    public LocalDate getFecha() { return fecha; }
    public UUID getClienteId() { return clienteId; }
    public TipoEvento getTipoEvento() { return tipoEvento; }
    public int getCantidadAdultos() { return cantidadAdultos; }
    public int getCantidadNinos() { return cantidadNinos; }
    public int getNumInvitados() { return numInvitados; }
    public ZonaEvento getZona() { return zona; }
    public UUID getLugarId() { return lugarId; }
    public EstadoEvento getEstado() { return estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}

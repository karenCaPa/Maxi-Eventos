package com.maxieventos.finanzas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adicionales_evento")
public class AdicionalEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;
    @Column(nullable = false)
    private String codigo;
    @Column(nullable = false)
    private String nombre;
    @Column(nullable = false)
    private int cantidad;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorUnitario;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    protected AdicionalEvento() {
    }

    public AdicionalEvento(UUID eventoId, String codigo, String nombre, int cantidad,
                          BigDecimal valorUnitario, BigDecimal subtotal) {
        this.eventoId = eventoId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario;
        this.subtotal = subtotal;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
    public Instant getCreadoEn() { return creadoEn; }
}

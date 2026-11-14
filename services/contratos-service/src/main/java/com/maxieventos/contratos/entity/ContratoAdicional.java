package com.maxieventos.contratos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Servicio adicional de un evento tal como lo publicó Finanzas (SERVICIO_ADICIONAL_AGREGADO): es la fuente del anexo del
 * contrato. Se guarda por eventoId aunque el contrato todavía no exista (aparece en el anexo cuando se genere).
 */
@Entity
@Table(name = "contrato_adicionales")
public class ContratoAdicional {
    @Id
    @Column(name = "adicional_id")
    private UUID adicionalId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "valor_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorUnitario;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(length = 10)
    private String moneda;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;

    protected ContratoAdicional() {
    }

    public ContratoAdicional(UUID adicionalId, UUID eventoId, String codigo, String nombre, int cantidad,
                             BigDecimal valorUnitario, BigDecimal subtotal, String moneda, Instant recibidoEn) {
        this.adicionalId = adicionalId;
        this.eventoId = eventoId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.valorUnitario = valorUnitario;
        this.subtotal = subtotal;
        this.moneda = moneda;
        this.recibidoEn = recibidoEn;
    }

    public UUID getAdicionalId() { return adicionalId; }
    public UUID getEventoId() { return eventoId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }
    public String getMoneda() { return moneda; }
    public Instant getRecibidoEn() { return recibidoEn; }
}

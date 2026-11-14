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
@Table(name = "pagos")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventoId;

    @Column(nullable = false)
    private UUID clienteId;

    @Column(nullable = false)
    private String metodoPago;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private String moneda;

    @Column(nullable = false)
    private String estado = "PAGADO";

    /**
     * Clave de idempotencia del abono. Índice UNIQUE {@code uk_pago_clave_idempotencia} (schema.sql): una repetición
     * concurrente con la misma clave la detecta la base de datos. Nula en los pagos anteriores a la Fase 4.
     */
    @Column(name = "clave_idempotencia", length = 100)
    private String claveIdempotencia;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    protected Pago() {
    }

    public Pago(UUID eventoId, UUID clienteId, String metodoPago, BigDecimal monto, String moneda,
                String claveIdempotencia) {
        this.eventoId = eventoId;
        this.clienteId = clienteId;
        this.metodoPago = metodoPago;
        this.monto = monto;
        this.moneda = moneda;
        this.claveIdempotencia = claveIdempotencia;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public UUID getClienteId() { return clienteId; }
    public String getMetodoPago() { return metodoPago; }
    public BigDecimal getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public String getEstado() { return estado; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public Instant getCreadoEn() { return creadoEn; }
}

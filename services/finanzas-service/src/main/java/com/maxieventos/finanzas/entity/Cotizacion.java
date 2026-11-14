package com.maxieventos.finanzas.entity;

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
import java.time.Instant;
import java.util.UUID;

/**
 * Cotización de un evento (una por evento). Los campos del desglose (tipo, adultos, niños, adicionales y subtotal)
 * se agregaron en la Fase 4 y son nulos en las cotizaciones anteriores.
 */
@Entity
@Table(name = "cotizaciones", uniqueConstraints =
        @UniqueConstraint(name = "uk_cotizacion_evento", columnNames = "evento_id"))
public class Cotizacion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", length = 20)
    private TipoEvento tipoEvento;
    /** Total de invitados (adultos + niños). */
    @Column(nullable = false)
    private int invitados;
    @Column(name = "cantidad_adultos")
    private Integer cantidadAdultos;
    @Column(name = "cantidad_ninos")
    private Integer cantidadNinos;
    @Column(nullable = false)
    private String zona;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorBase;
    @Column(name = "adultos_adicionales")
    private Integer adultosAdicionales;
    @Column(name = "ninos_adicionales")
    private Integer ninosAdicionales;
    @Column(name = "valor_adultos_adicionales", precision = 19, scale = 2)
    private BigDecimal valorAdultosAdicionales;
    @Column(name = "valor_ninos_adicionales", precision = 19, scale = 2)
    private BigDecimal valorNinosAdicionales;
    @Column(precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal recargoZona;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    /** Depósito inicial requerido para confirmar el evento. Nulo solo en cotizaciones anteriores a la Fase 3. */
    @Column(name = "monto_reserva_requerido", precision = 19, scale = 2)
    private BigDecimal montoReservaRequerido;
    @Column(nullable = false)
    private String moneda;
    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    protected Cotizacion() {
    }

    public Cotizacion(UUID eventoId, DesgloseCotizacion desglose, String moneda) {
        this.eventoId = eventoId;
        this.tipoEvento = desglose.tipoEvento();
        this.invitados = desglose.cantidadAdultos() + desglose.cantidadNinos();
        this.cantidadAdultos = desglose.cantidadAdultos();
        this.cantidadNinos = desglose.cantidadNinos();
        this.zona = desglose.zona().name();
        this.valorBase = desglose.valorBase();
        this.adultosAdicionales = desglose.adultosAdicionales();
        this.ninosAdicionales = desglose.ninosAdicionales();
        this.valorAdultosAdicionales = desglose.valorAdultosAdicionales();
        this.valorNinosAdicionales = desglose.valorNinosAdicionales();
        this.subtotal = desglose.subtotal();
        this.recargoZona = desglose.recargoZona();
        this.total = desglose.total();
        this.montoReservaRequerido = desglose.montoReservaRequerido();
        this.moneda = moneda;
    }

    public UUID getId() { return id; }
    public UUID getEventoId() { return eventoId; }
    public TipoEvento getTipoEvento() { return tipoEvento; }
    public int getInvitados() { return invitados; }
    public Integer getCantidadAdultos() { return cantidadAdultos; }
    public Integer getCantidadNinos() { return cantidadNinos; }
    public String getZona() { return zona; }
    public BigDecimal getValorBase() { return valorBase; }
    public Integer getAdultosAdicionales() { return adultosAdicionales; }
    public Integer getNinosAdicionales() { return ninosAdicionales; }
    public BigDecimal getValorAdultosAdicionales() { return valorAdultosAdicionales; }
    public BigDecimal getValorNinosAdicionales() { return valorNinosAdicionales; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getRecargoZona() { return recargoZona; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getMontoReservaRequerido() { return montoReservaRequerido; }
    public String getMoneda() { return moneda; }
    public Instant getCreadoEn() { return creadoEn; }
}

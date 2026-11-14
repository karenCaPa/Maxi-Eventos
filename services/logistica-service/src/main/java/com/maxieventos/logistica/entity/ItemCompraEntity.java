package com.maxieventos.logistica.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "items_compra")
public class ItemCompraEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String nombreItem;
    private String cantidadEstimada;
    private boolean comprado;

    /** 10B3: CALCULADO (lo genera el sistema) o MANUAL (lo agrega la organizadora). Las filas anteriores son CALCULADO. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenItemCompra origen = OrigenItemCompra.CALCULADO;

    /** 10B3: nota opcional (por ejemplo, dónde comprarlo). */
    @Column(length = 255)
    private String nota;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JsonIgnore
    @JoinColumn(name = "evento_id", nullable = false)
    private LogisticaEvento logisticaEvento;

    protected ItemCompraEntity() {
    }

    /** Ítem calculado por el sistema al confirmar el evento. */
    public ItemCompraEntity(String nombreItem, String cantidadEstimada) {
        this.nombreItem = nombreItem;
        this.cantidadEstimada = cantidadEstimada;
        this.comprado = false;
        this.origen = OrigenItemCompra.CALCULADO;
    }

    /** Ítem agregado a mano por la organizadora (10B3). */
    public static ItemCompraEntity manual(String nombreItem, String cantidadEstimada, String nota) {
        ItemCompraEntity item = new ItemCompraEntity(nombreItem, cantidadEstimada);
        item.origen = OrigenItemCompra.MANUAL;
        item.nota = nota;
        return item;
    }

    /** Cambia nombre, cantidad y nota; conserva el origen y la marca de comprado. */
    public void editar(String nombreItem, String cantidadEstimada, String nota) {
        this.nombreItem = nombreItem;
        this.cantidadEstimada = cantidadEstimada;
        this.nota = nota;
    }

    void setLogisticaEvento(LogisticaEvento logisticaEvento) { this.logisticaEvento = logisticaEvento; }
    public UUID getId() { return id; }
    public String getNombreItem() { return nombreItem; }
    public String getCantidadEstimada() { return cantidadEstimada; }
    public boolean isComprado() { return comprado; }
    public void setComprado(boolean comprado) { this.comprado = comprado; }
    public OrigenItemCompra getOrigen() { return origen; }
    public boolean esManual() { return origen == OrigenItemCompra.MANUAL; }
    public String getNota() { return nota; }
}

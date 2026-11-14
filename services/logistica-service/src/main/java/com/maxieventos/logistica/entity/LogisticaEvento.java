package com.maxieventos.logistica.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "logistica_eventos")
public class LogisticaEvento {
    @Id
    private UUID eventoId;
    private Integer meserosRequeridos;
    private Integer personalCocinaRequerido;
    private Integer camionesTransporte;

    @OneToMany(mappedBy = "logisticaEvento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCompraEntity> compras = new ArrayList<>();

    protected LogisticaEvento() {
    }

    public LogisticaEvento(UUID eventoId, int meseros, int cocina, int camiones) {
        this.eventoId = eventoId;
        this.meserosRequeridos = meseros;
        this.personalCocinaRequerido = cocina;
        this.camionesTransporte = camiones;
    }

    public void agregarCompra(ItemCompraEntity compra) {
        compras.add(compra);
        compra.setLogisticaEvento(this);
    }

    /** Suma el personal o vehículos extra de un servicio adicional. */
    public void sumar(int meseros, int cocina, int camiones) {
        this.meserosRequeridos += meseros;
        this.personalCocinaRequerido += cocina;
        this.camionesTransporte += camiones;
    }

    public UUID getEventoId() { return eventoId; }
    public Integer getMeserosRequeridos() { return meserosRequeridos; }
    public Integer getPersonalCocinaRequerido() { return personalCocinaRequerido; }
    public Integer getCamionesTransporte() { return camionesTransporte; }
    public List<ItemCompraEntity> getCompras() { return compras; }
}
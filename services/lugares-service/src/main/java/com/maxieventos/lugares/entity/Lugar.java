package com.maxieventos.lugares.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "lugares")
public class Lugar {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String nombre;
    private String direccion;
    private String tipo;
    private Integer capacidad;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLugar estado = EstadoLugar.ACTIVO;

    protected Lugar() {
    }

    public Lugar(String nombre, String direccion, String tipo, Integer capacidad) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.tipo = tipo;
        this.capacidad = capacidad;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getTipo() { return tipo; }
    public Integer getCapacidad() { return capacidad; }
    public EstadoLugar getEstado() { return estado; }
    public void actualizar(String nombre, String direccion, String tipo, Integer capacidad) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.tipo = tipo;
        this.capacidad = capacidad;
    }
    public void cambiarEstado(EstadoLugar estado) { this.estado = estado; }
}

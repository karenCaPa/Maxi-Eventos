package com.maxieventos.proveedores.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "proveedores")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private String contacto;
    @Column(nullable = false)
    private boolean activo = true;

    protected Proveedor() {
    }

    public Proveedor(String nombre, String categoria, String contacto) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.contacto = contacto;
    }

    public UUID getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }
    public String getContacto() { return contacto; }
    public boolean isActivo() { return activo; }
    public void actualizar(String nombre, String categoria, String contacto) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.contacto = contacto;
    }
    public void setActivo(boolean activo) { this.activo = activo; }
}

package com.maxieventos.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Usuario que puede iniciar sesión (Fase 11D). Se siembra al arrancar desde las variables de entorno (ver
 * {@code SembradoUsuarios}); la contraseña solo se guarda como hash BCrypt. El {@code id} es el {@code sub} del JWT.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    private UUID id;

    /** Siempre en minúsculas y sin espacios en los extremos (el login no distingue mayúsculas). */
    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected Usuario() {
    }

    public Usuario(UUID id, String correo, String nombre, Rol rol, String passwordHash) {
        this.id = id;
        this.creadoEn = Instant.now();
        actualizar(correo, nombre, rol, passwordHash);
    }

    /** Deja el usuario como dicen las variables de entorno (la siembra se repite en cada arranque). */
    public final void actualizar(String correo, String nombre, Rol rol, String passwordHash) {
        this.correo = normalizarCorreo(correo);
        this.nombre = nombre;
        this.rol = rol;
        this.passwordHash = passwordHash;
        this.activo = true;
        this.actualizadoEn = Instant.now();
    }

    public void desactivar() {
        this.activo = false;
        this.actualizadoEn = Instant.now();
    }

    public static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getCorreo() { return correo; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActivo() { return activo; }
}

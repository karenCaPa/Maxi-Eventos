package com.maxieventos.notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fase 11H: una notificación leída por un usuario de la interfaz (la campanita). "Leída" es POR USUARIO: la organizadora y
 * el asistente tienen cada uno sus lecturas. {@code usuario} = sub del JWT (id opaco, no el correo). Las filas se insertan con
 * SQL nativo ({@code ON CONFLICT DO NOTHING}), así que marcar dos veces o a la vez no falla ni duplica.
 */
@Entity
@Table(name = "notificaciones_lecturas")
@IdClass(LecturaNotificacion.Clave.class)
public class LecturaNotificacion {

    @Id
    @Column(name = "notificacion_id")
    private UUID notificacionId;

    @Id
    @Column(length = 100)
    private String usuario;

    @Column(name = "leida_en", nullable = false)
    private Instant leidaEn;

    protected LecturaNotificacion() {
    }

    public UUID getNotificacionId() { return notificacionId; }
    public String getUsuario() { return usuario; }
    public Instant getLeidaEn() { return leidaEn; }

    public static class Clave implements Serializable {
        private UUID notificacionId;
        private String usuario;

        public Clave() {
        }

        public Clave(UUID notificacionId, String usuario) {
            this.notificacionId = notificacionId;
            this.usuario = usuario;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(notificacionId, c.notificacionId) && Objects.equals(usuario, c.usuario);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificacionId, usuario);
        }
    }
}

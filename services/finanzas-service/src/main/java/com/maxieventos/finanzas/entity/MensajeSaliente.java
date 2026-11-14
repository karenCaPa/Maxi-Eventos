package com.maxieventos.finanzas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje Kafka pendiente de publicar (patrón outbox). Se guarda en la MISMA transacción que el cambio de negocio, así
 * que existe si y solo si el cambio se confirmó. El id es el {@code idEvento} del sobre: es el mismo en cada reintento
 * (los consumidores descartan los duplicados por ese id). Las filas PUBLICADO se conservan como registro.
 */
@Entity
@Table(name = "finanzas_mensajes_salientes")
public class MensajeSaliente {
    @Id
    @Column(name = "id_evento")
    private UUID idEvento;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 100)
    private String topico;

    @Column(nullable = false, length = 100)
    private String clave;

    /** Sobre completo en JSON, tal como se publica. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMensajeSaliente estado = EstadoMensajeSaliente.PENDIENTE;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "tomado_en")
    private Instant tomadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected MensajeSaliente() {
    }

    public MensajeSaliente(UUID idEvento, String tipo, String topico, String clave, String payload, Instant ahora) {
        this.idEvento = idEvento;
        this.tipo = tipo;
        this.topico = topico;
        this.clave = clave;
        this.payload = payload;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    public UUID getIdEvento() { return idEvento; }
    public String getTipo() { return tipo; }
    public String getTopico() { return topico; }
    public String getClave() { return clave; }
    public String getPayload() { return payload; }
    public EstadoMensajeSaliente getEstado() { return estado; }
    public int getIntentos() { return intentos; }
    public String getUltimoError() { return ultimoError; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Instant getTomadoEn() { return tomadoEn; }
    public Instant getPublicadoEn() { return publicadoEn; }
}

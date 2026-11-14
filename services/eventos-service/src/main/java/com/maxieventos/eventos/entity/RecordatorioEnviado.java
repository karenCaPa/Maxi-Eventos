package com.maxieventos.eventos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Un recordatorio EVENTO_PROXIMO ya enviado (Fase 11E): a lo sumo uno por evento y umbral, aunque la tarea se ejecute
 * muchas veces (la programada y la manual). Se guarda en la MISMA transacción que la fila del outbox: o existen los dos o
 * ninguno. La clave primaria es el {@code idEvento} determinista del mensaje y además hay una restricción única
 * {@code (evento_id, umbral_dias)}.
 */
@Entity
@Table(name = "eventos_recordatorios_enviados",
        uniqueConstraints = @UniqueConstraint(name = "uk_recordatorio_evento_umbral", columnNames = {"evento_id", "umbral_dias"}))
public class RecordatorioEnviado implements Persistable<UUID> {

    @Id
    @Column(name = "id_evento")
    private UUID idEvento;

    /**
     * Fase 11G: siempre es una fila NUEVA. Con el id asignado (determinista), Spring Data haría un merge: si otra ejecución
     * simultánea ya había confirmado la misma fila, el merge la encontraba y la ACTUALIZABA en lugar de chocar con la clave,
     * y esa ejecución contaba un ENVIADO de más (prueba dosEjecucionesALaVezTampocoDuplican, intermitente). Con isNew = true
     * es un INSERT: la clave primaria y la restricción única lo rechazan y se cuenta como YA_ENVIADO.
     */
    @Transient
    private boolean nuevo = true;

    @Override
    public UUID getId() { return idEvento; }

    @Override
    public boolean isNew() { return nuevo; }

    @PostLoad
    void alCargar() { nuevo = false; }

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "umbral_dias", nullable = false)
    private int umbralDias;

    @Column(name = "dias_restantes", nullable = false)
    private int diasRestantes;

    @Column(name = "fecha_evento", nullable = false)
    private LocalDate fechaEvento;

    @Column(name = "enviado_en", nullable = false)
    private Instant enviadoEn;

    protected RecordatorioEnviado() {
    }

    public RecordatorioEnviado(UUID idEvento, UUID eventoId, int umbralDias, int diasRestantes, LocalDate fechaEvento,
                               Instant enviadoEn) {
        this.idEvento = idEvento;
        this.eventoId = eventoId;
        this.umbralDias = umbralDias;
        this.diasRestantes = diasRestantes;
        this.fechaEvento = fechaEvento;
        this.enviadoEn = enviadoEn;
    }

    public UUID getIdEvento() { return idEvento; }
    public UUID getEventoId() { return eventoId; }
    public int getUmbralDias() { return umbralDias; }
    public int getDiasRestantes() { return diasRestantes; }
    public LocalDate getFechaEvento() { return fechaEvento; }
    public Instant getEnviadoEn() { return enviadoEn; }
}

package com.maxieventos.contratos.entity;

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
 * Mensaje Kafka ya procesado por un consumer group (idempotencia). Clave (idEvento, consumerGroup): un mismo mensaje
 * se procesa una sola vez por grupo. Se inserta en la MISMA transacción que el cambio de negocio.
 */
@Entity
@Table(name = "contratos_eventos_procesados")
@IdClass(EventoProcesado.Clave.class)
public class EventoProcesado {
    @Id
    @Column(name = "id_evento")
    private UUID idEvento;

    @Id
    @Column(name = "consumer_group", length = 100)
    private String consumerGroup;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(nullable = false, length = 100)
    private String topico;

    @Column(nullable = false)
    private int particion;

    @Column(name = "offset_kafka", nullable = false)
    private long offsetKafka;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }

    public EventoProcesado(UUID idEvento, String consumerGroup, String tipo, UUID eventoId, String topico, int particion,
                           long offsetKafka, Instant procesadoEn) {
        this.idEvento = idEvento;
        this.consumerGroup = consumerGroup;
        this.tipo = tipo;
        this.eventoId = eventoId;
        this.topico = topico;
        this.particion = particion;
        this.offsetKafka = offsetKafka;
        this.procesadoEn = procesadoEn;
    }

    public UUID getIdEvento() { return idEvento; }
    public String getConsumerGroup() { return consumerGroup; }
    public String getTipo() { return tipo; }

    public static class Clave implements Serializable {
        private UUID idEvento;
        private String consumerGroup;

        protected Clave() {
        }

        public Clave(UUID idEvento, String consumerGroup) {
            this.idEvento = idEvento;
            this.consumerGroup = consumerGroup;
        }

        @Override
        public boolean equals(Object otro) {
            return otro instanceof Clave c && Objects.equals(idEvento, c.idEvento) && Objects.equals(consumerGroup, c.consumerGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idEvento, consumerGroup);
        }
    }
}

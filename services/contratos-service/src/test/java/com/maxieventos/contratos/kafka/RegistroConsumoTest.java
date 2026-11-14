package com.maxieventos.contratos.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** La línea de log del consumidor tiene un formato fijo (se busca en las evidencias). */
class RegistroConsumoTest {

    @Test
    void lineaConTodosLosCampos() {
        UUID idEvento = UUID.fromString("6b1d4a2e-1c7f-4f3a-9e0a-2f5d8c9b7a11");
        UUID eventoId = UUID.fromString("0f8c2b7e-9a41-4d0e-b3a5-7c2e1f6d9a00");
        MensajeKafka mensaje = new MensajeKafka(idEvento, "SERVICIO_ADICIONAL_AGREGADO", 1, Instant.now(), "finanzas-service", eventoId, null);

        String linea = RegistroConsumo.linea("contratos-group",
                new ConsumerRecord<>("servicios-adicionales.agregados", 2, 15L, eventoId.toString(), mensaje), "PROCESADO", "meseros=7");

        assertThat(linea).isEqualTo("Consumo Kafka. grupo=contratos-group, topic=servicios-adicionales.agregados, particion=2, offset=15, "
                + "idEvento=6b1d4a2e-1c7f-4f3a-9e0a-2f5d8c9b7a11, tipo=SERVICIO_ADICIONAL_AGREGADO, "
                + "eventoId=0f8c2b7e-9a41-4d0e-b3a5-7c2e1f6d9a00, resultado=PROCESADO, detalle=meseros=7");
    }

    @Test
    void mensajeIlegibleSinSobre() {
        String linea = RegistroConsumo.linea("contratos-group",
                new ConsumerRecord<>("servicios-adicionales.agregados", 0, 3L, "k", null), "INVALIDO_A_DLT", null);

        assertThat(linea).isEqualTo("Consumo Kafka. grupo=contratos-group, topic=servicios-adicionales.agregados, particion=0, offset=3, "
                + "idEvento=-, tipo=-, eventoId=-, resultado=INVALIDO_A_DLT");
    }
}

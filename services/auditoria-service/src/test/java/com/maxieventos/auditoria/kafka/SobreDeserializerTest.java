package com.maxieventos.auditoria.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class SobreDeserializerTest {

    private final SobreDeserializer deserializer = new SobreDeserializer();

    private static final String JSON = """
            {"idEvento":"6b1d4a2e-1c7f-4f3a-9e0a-2f5d8c9b7a11","tipo":"PAGO_REGISTRADO","version":1,
             "fechaOcurrencia":"2026-10-28T15:30:00Z","servicioOrigen":"finanzas-service",
             "eventoId":"0f8c2b7e-9a41-4d0e-b3a5-7c2e1f6d9a00","campoNuevo":"x",
             "datos":{"monto":1000000,"metodoPago":"TRANSFERENCIA","nota":"Señora María"}}""";

    @Test
    void leeElSobreYConservaElTextoOriginalExacto() {
        MensajeKafka mensaje = deserializer.deserialize("pagos.registrados", JSON.getBytes(StandardCharsets.UTF_8));

        assertThat(mensaje.idEvento()).isEqualTo(UUID.fromString("6b1d4a2e-1c7f-4f3a-9e0a-2f5d8c9b7a11"));
        assertThat(mensaje.tipo()).isEqualTo("PAGO_REGISTRADO");
        assertThat(mensaje.fechaOcurrencia()).isEqualTo(Instant.parse("2026-10-28T15:30:00Z"));
        assertThat(mensaje.datos().get("nota").asText()).isEqualTo("Señora María");
        assertThat(mensaje.textoOriginal()).isEqualTo(JSON);
    }

    @Test
    void textoIlegibleLanzaSerializationException() {
        assertThatThrownBy(() -> deserializer.deserialize("pagos.registrados", "esto no es json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void elTextoOriginalNoSeSerializa() throws Exception {
        MensajeKafka mensaje = deserializer.deserialize("pagos.registrados", JSON.getBytes(StandardCharsets.UTF_8));

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(mensaje);

        assertThat(json).doesNotContain("textoOriginal").contains("\"idEvento\"");
    }

    @Test
    void valorNuloEsNulo() {
        assertThat(deserializer.deserialize("pagos.registrados", null)).isNull();
    }
}

package com.maxieventos.auditoria.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Delegado del {@code ErrorHandlingDeserializer}: lee el sobre como lo hace el {@code JsonDeserializer} de los demás
 * consumidores (ignora campos desconocidos) y además guarda el JSON original del tópico en
 * {@link MensajeKafka#textoOriginal()}, que es lo que la auditoría persiste en {@code payload}.
 * Si el texto no es JSON lanza {@link SerializationException}: el {@code ErrorHandlingDeserializer} la convierte en
 * {@code DeserializationException} con los bytes originales y el mensaje va al DLT sin reintentos.
 */
public class SobreDeserializer implements Deserializer<MensajeKafka> {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public MensajeKafka deserialize(String topico, byte[] datos) {
        if (datos == null) {
            return null;
        }
        String texto = new String(datos, StandardCharsets.UTF_8);
        try {
            return mapper.readValue(datos, MensajeKafka.class).conTextoOriginal(texto);
        } catch (IOException ex) {
            throw new SerializationException("El mensaje de " + topico + " no es un sobre JSON legible", ex);
        }
    }
}

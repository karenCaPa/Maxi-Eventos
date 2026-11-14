package com.maxieventos.eventos.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

/**
 * El serializador configurado por código es compatible con las propiedades del productor del application.yml real
 * (Spring Kafka falla en el primer envío si el JsonSerializer se configura por código y por propiedades a la vez).
 */
class KafkaProducerConfigTest {

    @Test
    void elProductorSeCreaConLasPropiedadesDelApplicationYml() throws Exception {
        StandardEnvironment entorno = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(entorno.getPropertySources()::addLast);
        Map<String, Object> config = new HashMap<>(Binder.get(entorno)
                .bind("spring.kafka.producer.properties", Bindable.mapOf(String.class, Object.class)).orElse(Map.of()));
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, Object> fabrica = new DefaultKafkaProducerFactory<>(config);

        new KafkaProducerConfig().serializadorDeMensajes(new ObjectMapper()).customize(fabrica);

        assertThatCode(() -> fabrica.createProducer().close()).doesNotThrowAnyException();
    }
}

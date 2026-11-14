package com.maxieventos.finanzas.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Tópicos que produce finanzas-service (cada tópico lo declara su productor, con 3 particiones). */
@Configuration
public class KafkaTopicConfig {
    public static final String PAGOS_REGISTRADOS = "pagos.registrados";
    public static final String SERVICIOS_ADICIONALES_AGREGADOS = "servicios-adicionales.agregados";

    @Bean
    NewTopic pagosRegistradosTopic() {
        return TopicBuilder.name(PAGOS_REGISTRADOS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic serviciosAdicionalesAgregadosTopic() {
        return TopicBuilder.name(SERVICIOS_ADICIONALES_AGREGADOS).partitions(3).replicas(1).build();
    }
}

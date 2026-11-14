package com.maxieventos.eventos.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    public static final String EVENTOS_CONFIRMADOS = "eventos.confirmados";
    /** Fase 11E: recordatorios EVENTO_PROXIMO (lo consumen notificaciones y auditoría). */
    public static final String EVENTOS_PROXIMOS = "eventos.proximos";

    @Bean
    NewTopic eventosConfirmadosTopic() {
        return TopicBuilder.name(EVENTOS_CONFIRMADOS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eventosProximosTopic() {
        return TopicBuilder.name(EVENTOS_PROXIMOS).partitions(3).replicas(1).build();
    }
}

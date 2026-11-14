package com.maxieventos.eventos.config;

import com.maxieventos.eventos.service.UmbralesRecordatorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Recordatorios EVENTO_PROXIMO (Fase 11E): umbrales en días ({@code maxi-eventos.recordatorios.dias}, variable
 * {@code RECORDATORIOS_DIAS}, por defecto 7 y 1). El horario de la tarea está en {@code maxi-eventos.recordatorios.cron}
 * ({@code RECORDATORIOS_CRON}; "-" la desactiva y solo queda la ejecución manual) con la zona de
 * {@code maxi-eventos.zona-horaria}. Se registran en el log al arrancar.
 */
@Configuration
public class RecordatoriosConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordatoriosConfig.class);

    @Bean
    UmbralesRecordatorio umbralesRecordatorio(@Value("${maxi-eventos.recordatorios.dias}") String dias,
                                              @Value("${maxi-eventos.recordatorios.cron}") String cron,
                                              @Value("${maxi-eventos.zona-horaria}") String zona) {
        UmbralesRecordatorio umbrales = UmbralesRecordatorio.desde(dias);
        LOGGER.info("Recordatorios de eventos próximos: umbrales={} días, tarea={} ({})", umbrales.dias(),
                "-".equals(cron.trim()) ? "desactivada (solo manual)" : cron, zona);
        return umbrales;
    }
}

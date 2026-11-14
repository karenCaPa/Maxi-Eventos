package com.maxieventos.eventos.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Hoy" se calcula en la zona horaria del negocio (Neiva), no en la del contenedor (UTC): así una fecha de mañana
 * no se rechaza por la noche. Inyectable para fijar la fecha en las pruebas.
 * La zona sale de {@code maxi-eventos.zona-horaria} en application.yml (variable de entorno {@code ZONA_HORARIA}).
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${maxi-eventos.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}

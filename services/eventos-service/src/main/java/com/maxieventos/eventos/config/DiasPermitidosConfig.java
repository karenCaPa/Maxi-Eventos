package com.maxieventos.eventos.config;

import com.maxieventos.eventos.service.DiasPermitidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Días de la semana en que la empresa trabaja: {@code maxi-eventos.dias-permitidos} en application.yml (variable de
 * entorno {@code DIAS_PERMITIDOS}; por defecto FRIDAY,SATURDAY,SUNDAY). Un valor inválido impide arrancar.
 */
@Configuration
public class DiasPermitidosConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiasPermitidosConfig.class);

    @Bean
    public DiasPermitidos diasPermitidos(@Value("${maxi-eventos.dias-permitidos}") String valor) {
        DiasPermitidos dias = DiasPermitidos.desde(valor);
        LOGGER.info("Días permitidos para eventos: {}", dias.dias());
        return dias;
    }
}

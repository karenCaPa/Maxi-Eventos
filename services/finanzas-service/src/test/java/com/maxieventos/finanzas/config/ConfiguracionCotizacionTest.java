package com.maxieventos.finanzas.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.maxieventos.finanzas.config.ConfiguracionCotizacion.ModoCupo;
import com.maxieventos.finanzas.entity.TipoEvento;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Las claves de {@code finanzas.cotizacion} del application.yml real se enlazan con {@link ConfiguracionCotizacion}. */
class ConfiguracionCotizacionTest {

    @Test
    void applicationYmlTieneTodasLasTarifas() throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(entorno.getPropertySources()::addLast);

        ConfiguracionCotizacion config = Binder.get(entorno)
                .bind("finanzas.cotizacion", ConfiguracionCotizacion.class).get();

        assertThat(config.modoCupo()).isEqualTo(ModoCupo.SEPARADO);
        assertThat(config.recargoRural()).isEqualByComparingTo("0.10");
        for (TipoEvento tipo : TipoEvento.values()) {
            ConfiguracionCotizacion.Tarifa tarifa = config.tarifa(tipo);
            assertThat(tarifa).as("tarifa de %s", tipo).isNotNull();
            assertThat(tarifa.valorBase()).isPositive();
            assertThat(tarifa.adultosIncluidos()).isNotNull();
            assertThat(tarifa.ninosIncluidos()).isNotNull();
            assertThat(tarifa.invitadosIncluidos()).isNotNull();
            assertThat(tarifa.valorAdultoAdicional()).isNotNull();
            assertThat(tarifa.valorNinoAdicional()).isNotNull();
        }
        assertThat(config.tarifa(TipoEvento.BODA).valorBase()).isEqualByComparingTo("12000000");
    }
}

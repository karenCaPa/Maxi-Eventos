package com.maxieventos.finanzas.config;

import com.maxieventos.finanzas.entity.TipoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tarifas de la cotización ({@code finanzas.cotizacion} en application.yml). Todas son valores de ejemplo marcados con
 * {@code # TODO valor real}; se validan al arrancar (un valor inválido impide iniciar el servicio).
 */
@Validated
@ConfigurationProperties(prefix = "finanzas.cotizacion")
public record ConfiguracionCotizacion(
        @NotNull ModoCupo modoCupo,
        /** Porcentaje (0.10 = 10 %) que se suma al subtotal en zona RURAL. */
        @NotNull @DecimalMin("0") BigDecimal recargoRural,
        @Valid @NotNull Tarifas tarifas) {

    /**
     * Cómo se cuenta el cupo de invitados incluidos en el valor base.
     * PROVISIONAL: falta confirmar con la organizadora cuál de los dos modos aplica Maxi Eventos.
     */
    public enum ModoCupo {
        /** Adultos y niños tienen cupos separados (adultos-incluidos y ninos-incluidos). */
        SEPARADO,
        /** Un solo cupo (invitados-incluidos) que cubre primero a los adultos y lo que sobre, a los niños. */
        UNICO
    }

    public record Tarifas(@Valid @NotNull Tarifa quinceAnos, @Valid @NotNull Tarifa boda) {
    }

    public record Tarifa(
            @NotNull @Positive BigDecimal valorBase,
            /** Modo SEPARADO. */
            @NotNull @PositiveOrZero Integer adultosIncluidos,
            /** Modo SEPARADO. */
            @NotNull @PositiveOrZero Integer ninosIncluidos,
            /** Modo UNICO. */
            @NotNull @PositiveOrZero Integer invitadosIncluidos,
            @NotNull @PositiveOrZero BigDecimal valorAdultoAdicional,
            @NotNull @PositiveOrZero BigDecimal valorNinoAdicional) {
    }

    public Tarifa tarifa(TipoEvento tipo) {
        return switch (tipo) {
            case QUINCE_ANOS -> tarifas.quinceAnos();
            case BODA -> tarifas.boda();
        };
    }
}

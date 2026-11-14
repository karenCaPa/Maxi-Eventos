package com.maxieventos.finanzas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maxieventos.finanzas.config.ConfiguracionCotizacion;
import com.maxieventos.finanzas.config.ConfiguracionCotizacion.ModoCupo;
import com.maxieventos.finanzas.config.ConfiguracionCotizacion.Tarifa;
import com.maxieventos.finanzas.config.ConfiguracionCotizacion.Tarifas;
import com.maxieventos.finanzas.entity.DesgloseCotizacion;
import com.maxieventos.finanzas.entity.TipoEvento;
import com.maxieventos.finanzas.entity.ZonaEvento;
import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CalculadoraCotizacionTest {

    /** Mismos valores de ejemplo que application.yml. */
    static ConfiguracionCotizacion configuracion(ModoCupo modo) {
        return new ConfiguracionCotizacion(modo, new BigDecimal("0.10"), new Tarifas(
                new Tarifa(new BigDecimal("8000000"), 80, 20, 100, new BigDecimal("70000"), new BigDecimal("40000")),
                new Tarifa(new BigDecimal("12000000"), 100, 10, 110, new BigDecimal("90000"), new BigDecimal("50000"))));
    }

    static CalculadoraCotizacion calculadora(ModoCupo modo) {
        return new CalculadoraCotizacion(configuracion(modo), new BigDecimal("0.30"));
    }

    private static BigDecimal pesos(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    @Nested
    class CupoSeparado {
        private final CalculadoraCotizacion calculadora = calculadora(ModoCupo.SEPARADO);

        @Test
        void dentroDelCupoSoloPagaElValorBase() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 80, 20);

            assertThat(d.adultosAdicionales()).isZero();
            assertThat(d.ninosAdicionales()).isZero();
            assertThat(d.subtotal()).isEqualTo(pesos("8000000"));
            assertThat(d.recargoZona()).isEqualTo(pesos("0"));
            assertThat(d.total()).isEqualTo(pesos("8000000"));
            assertThat(d.montoReservaRequerido()).isEqualTo(pesos("2400000"));
        }

        @Test
        void adultosAdicionales() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 90, 15);

            assertThat(d.adultosAdicionales()).isEqualTo(10);
            assertThat(d.valorAdultosAdicionales()).isEqualTo(pesos("700000"));
            assertThat(d.ninosAdicionales()).isZero();
            assertThat(d.total()).isEqualTo(pesos("8700000"));
            assertThat(d.montoReservaRequerido()).isEqualTo(pesos("2610000"));
        }

        @Test
        void ninosAdicionalesAunqueSobrenCuposDeAdultos() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 60, 30);

            assertThat(d.adultosAdicionales()).isZero();
            assertThat(d.ninosAdicionales()).isEqualTo(10);
            assertThat(d.valorNinosAdicionales()).isEqualTo(pesos("400000"));
            assertThat(d.total()).isEqualTo(pesos("8400000"));
        }

        @Test
        void bodaRuralConRecargoSobreElSubtotal() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.BODA, ZonaEvento.RURAL, 120, 12);

            assertThat(d.valorBase()).isEqualTo(pesos("12000000"));
            assertThat(d.valorAdultosAdicionales()).isEqualTo(pesos("1800000"));
            assertThat(d.valorNinosAdicionales()).isEqualTo(pesos("100000"));
            assertThat(d.subtotal()).isEqualTo(pesos("13900000"));
            assertThat(d.recargoZona()).isEqualTo(pesos("1390000"));
            assertThat(d.total()).isEqualTo(pesos("15290000"));
            assertThat(d.montoReservaRequerido()).isEqualTo(pesos("4587000"));
        }

        @Test
        void elMismoEventoEnNeivaNoLlevaRecargo() {
            DesgloseCotizacion rural = calculadora.calcular(TipoEvento.BODA, ZonaEvento.RURAL, 50, 0);
            DesgloseCotizacion neiva = calculadora.calcular(TipoEvento.BODA, ZonaEvento.NEIVA, 50, 0);

            assertThat(neiva.total()).isEqualTo(pesos("12000000"));
            assertThat(rural.total()).isEqualTo(pesos("13200000"));
        }

        @Test
        void conservaLosDatosDelEvento() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.BODA, ZonaEvento.RURAL, 120, 12);

            assertThat(d.tipoEvento()).isEqualTo(TipoEvento.BODA);
            assertThat(d.zona()).isEqualTo(ZonaEvento.RURAL);
            assertThat(d.cantidadAdultos()).isEqualTo(120);
            assertThat(d.cantidadNinos()).isEqualTo(12);
        }
    }

    @Nested
    class CupoUnico {
        private final CalculadoraCotizacion calculadora = calculadora(ModoCupo.UNICO);

        @Test
        void elCupoSobranteDeAdultosCubreNinos() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 60, 30);

            assertThat(d.adultosAdicionales()).isZero();
            assertThat(d.ninosAdicionales()).isZero();
            assertThat(d.total()).isEqualTo(pesos("8000000"));
        }

        @Test
        void ninosQueExcedenElCupo() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 90, 20);

            assertThat(d.adultosAdicionales()).isZero();
            assertThat(d.ninosAdicionales()).isEqualTo(10);
            assertThat(d.total()).isEqualTo(pesos("8400000"));
        }

        @Test
        void adultosQueExcedenElCupoYTodosLosNinosSonAdicionales() {
            DesgloseCotizacion d = calculadora.calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.NEIVA, 110, 5);

            assertThat(d.adultosAdicionales()).isEqualTo(10);
            assertThat(d.ninosAdicionales()).isEqualTo(5);
            assertThat(d.total()).isEqualTo(pesos("8900000"));
        }
    }

    @Test
    void eventoSinInvitadosEsInvalido() {
        assertThatThrownBy(() -> calculadora(ModoCupo.SEPARADO).calcular(TipoEvento.BODA, ZonaEvento.NEIVA, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void porcentajeDeReservaFueraDeRangoImpideArrancar() {
        assertThatThrownBy(() -> new CalculadoraCotizacion(configuracion(ModoCupo.SEPARADO), new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalculadoraCotizacion(configuracion(ModoCupo.SEPARADO), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losValoresSalenDeLaConfiguracion() {
        ConfiguracionCotizacion otra = new ConfiguracionCotizacion(ModoCupo.SEPARADO, new BigDecimal("0.20"), new Tarifas(
                new Tarifa(new BigDecimal("1000000"), 10, 5, 15, new BigDecimal("10000"), new BigDecimal("5000")),
                new Tarifa(new BigDecimal("2000000"), 10, 5, 15, new BigDecimal("20000"), new BigDecimal("8000"))));

        DesgloseCotizacion d = new CalculadoraCotizacion(otra, new BigDecimal("0.50"))
                .calcular(TipoEvento.QUINCE_ANOS, ZonaEvento.RURAL, 12, 6);

        // 1.000.000 + 2 × 10.000 + 1 × 5.000 = 1.025.000; recargo 20 % = 205.000; total 1.230.000; reserva 50 %
        assertThat(d.subtotal()).isEqualTo(pesos("1025000"));
        assertThat(d.total()).isEqualTo(pesos("1230000"));
        assertThat(d.montoReservaRequerido()).isEqualTo(pesos("615000"));
    }
}

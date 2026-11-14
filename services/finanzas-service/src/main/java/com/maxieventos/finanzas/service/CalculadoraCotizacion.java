package com.maxieventos.finanzas.service;

import com.maxieventos.finanzas.config.ConfiguracionCotizacion;
import com.maxieventos.finanzas.config.ConfiguracionCotizacion.ModoCupo;
import com.maxieventos.finanzas.config.ConfiguracionCotizacion.Tarifa;
import com.maxieventos.finanzas.entity.DesgloseCotizacion;
import com.maxieventos.finanzas.entity.TipoEvento;
import com.maxieventos.finanzas.entity.ZonaEvento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cálculo de la cotización a partir de la configuración (nada escrito en el código):
 * <pre>
 * subtotal = valor base + adultos adicionales × valor adulto + niños adicionales × valor niño
 * recargo  = subtotal × recargo-rural   (solo zona RURAL)
 * total    = subtotal + recargo
 * reserva  = total × porcentaje de reserva
 * </pre>
 * Los invitados adicionales dependen del modo del cupo ({@link ModoCupo}).
 */
@Component
public class CalculadoraCotizacion {

    private final ConfiguracionCotizacion configuracion;
    private final BigDecimal porcentajeReserva;

    public CalculadoraCotizacion(ConfiguracionCotizacion configuracion,
                                 @Value("${finanzas.reserva.porcentaje}") BigDecimal porcentajeReserva) {
        if (porcentajeReserva.signum() <= 0 || porcentajeReserva.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("finanzas.reserva.porcentaje debe estar entre 0 (excluido) y 1");
        }
        this.configuracion = configuracion;
        this.porcentajeReserva = porcentajeReserva;
    }

    public DesgloseCotizacion calcular(TipoEvento tipo, ZonaEvento zona, int adultos, int ninos) {
        if (adultos < 0 || ninos < 0 || adultos + ninos == 0) {
            throw new IllegalArgumentException("El evento debe tener al menos un invitado");
        }
        Tarifa tarifa = configuracion.tarifa(tipo);
        int adultosAdicionales;
        int ninosAdicionales;
        if (configuracion.modoCupo() == ModoCupo.SEPARADO) {
            adultosAdicionales = Math.max(0, adultos - tarifa.adultosIncluidos());
            ninosAdicionales = Math.max(0, ninos - tarifa.ninosIncluidos());
        } else {
            int cupoParaNinos = Math.max(0, tarifa.invitadosIncluidos() - adultos);
            adultosAdicionales = Math.max(0, adultos - tarifa.invitadosIncluidos());
            ninosAdicionales = Math.max(0, ninos - cupoParaNinos);
        }
        BigDecimal valorBase = dinero(tarifa.valorBase());
        BigDecimal valorAdultos = dinero(tarifa.valorAdultoAdicional().multiply(BigDecimal.valueOf(adultosAdicionales)));
        BigDecimal valorNinos = dinero(tarifa.valorNinoAdicional().multiply(BigDecimal.valueOf(ninosAdicionales)));
        BigDecimal subtotal = valorBase.add(valorAdultos).add(valorNinos);
        BigDecimal recargo = zona == ZonaEvento.RURAL ? dinero(subtotal.multiply(configuracion.recargoRural())) : dinero(BigDecimal.ZERO);
        BigDecimal total = subtotal.add(recargo);
        return new DesgloseCotizacion(tipo, zona, adultos, ninos, valorBase, adultosAdicionales, ninosAdicionales,
                valorAdultos, valorNinos, subtotal, recargo, total, reserva(total));
    }

    /** Depósito inicial requerido para confirmar el evento. */
    public BigDecimal reserva(BigDecimal total) {
        return dinero(total.multiply(porcentajeReserva));
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}

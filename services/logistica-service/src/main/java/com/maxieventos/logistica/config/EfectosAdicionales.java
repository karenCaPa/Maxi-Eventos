package com.maxieventos.logistica.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Efecto de cada servicio adicional en los requerimientos de logística ({@code maxi-eventos.logistica.efectos-adicionales}
 * en application.yml). Código del adicional → personal o vehículos extra por unidad. Un código que no aparece no tiene
 * efecto. PROVISIONAL (# TODO regla real): falta confirmarlo con la organizadora.
 */
@ConfigurationProperties(prefix = "maxi-eventos.logistica")
public record EfectosAdicionales(Map<String, Efecto> efectosAdicionales) {

    public record Efecto(int meserosPorUnidad, int cocinaPorUnidad, int camionesPorUnidad) {
        public static final Efecto NINGUNO = new Efecto(0, 0, 0);

        public boolean sinEfecto() {
            return meserosPorUnidad == 0 && cocinaPorUnidad == 0 && camionesPorUnidad == 0;
        }
    }

    public Efecto de(String codigo) {
        return efectosAdicionales == null ? Efecto.NINGUNO : efectosAdicionales.getOrDefault(codigo, Efecto.NINGUNO);
    }
}

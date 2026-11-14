package com.maxieventos.eventos.service;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/**
 * Umbrales de los recordatorios EVENTO_PROXIMO en días antes del evento (Fase 11E; {@code RECORDATORIOS_DIAS}, por defecto
 * 7 y 1). Para un evento a {@code d} días se envía el umbral MÁS PEQUEÑO que aplique ({@code u >= d}): así, si la tarea no
 * corrió el día exacto del umbral (por ejemplo, el servicio estaba detenido), igual se avisa al día siguiente con los días
 * que faltan de verdad, y un evento a 1 día recibe el de 1 aunque ya hubiera recibido el de 7.
 */
public final class UmbralesRecordatorio {

    private final List<Integer> dias;

    private UmbralesRecordatorio(List<Integer> dias) {
        this.dias = dias;
    }

    /** "7,1" → [1, 7]. Vacío, no numérico o ≤ 0 → error (el servicio no arranca). */
    public static UmbralesRecordatorio desde(String configuracion) {
        List<Integer> valores;
        try {
            valores = Arrays.stream((configuracion == null ? "" : configuracion).split(","))
                    .map(String::trim).filter(t -> !t.isEmpty()).map(Integer::valueOf).distinct().sorted().toList();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("RECORDATORIOS_DIAS debe ser una lista de números enteros, por ejemplo 7,1");
        }
        if (valores.isEmpty() || valores.get(0) <= 0) {
            throw new IllegalArgumentException("RECORDATORIOS_DIAS debe tener al menos un número de días mayor que 0");
        }
        return new UmbralesRecordatorio(valores);
    }

    public List<Integer> dias() {
        return dias;
    }

    public int maximo() {
        return dias.get(dias.size() - 1);
    }

    /** El umbral más pequeño con {@code umbral >= diasRestantes}; vacío si el evento está más lejos que el mayor o ya pasó. */
    public OptionalInt aplicable(long diasRestantes) {
        if (diasRestantes < 1) {
            return OptionalInt.empty();
        }
        return dias.stream().filter(u -> u >= diasRestantes).mapToInt(Integer::intValue).findFirst();
    }
}

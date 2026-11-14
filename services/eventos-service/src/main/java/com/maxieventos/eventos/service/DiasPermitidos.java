package com.maxieventos.eventos.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Regla de negocio: la empresa solo trabaja ciertos días de la semana, así que solo se registran eventos en esos días
 * ({@code maxi-eventos.dias-permitidos} en application.yml; por defecto viernes, sábado y domingo, la misma definición de
 * fin de semana de {@link ReglaFinDeSemana}). La regla se aplica al crear un evento, al listar las fechas disponibles y al
 * bloquear una fecha a mano; los eventos ya registrados en otros días siguen siendo válidos.
 */
public final class DiasPermitidos {

    // Debe declararse antes que FIN_DE_SEMANA: el constructor lo usa para armar el mensaje
    private static final List<String> NOMBRES =
            List.of("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo");

    /** El de la configuración por defecto (y el de las pruebas). */
    public static final DiasPermitidos FIN_DE_SEMANA =
            new DiasPermitidos(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

    private final Set<DayOfWeek> dias;
    private final String mensaje;

    public DiasPermitidos(Set<DayOfWeek> dias) {
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("maxi-eventos.dias-permitidos debe tener al menos un día");
        }
        this.dias = Collections.unmodifiableSet(EnumSet.copyOf(dias));
        this.mensaje = "Solo se pueden registrar eventos en " + enumerar() + ".";
    }

    /**
     * Lee la lista de la configuración ("FRIDAY,SATURDAY,SUNDAY", sin distinguir mayúsculas). Un día que no existe o una
     * lista vacía detienen el arranque del servicio con un mensaje claro.
     */
    public static DiasPermitidos desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("maxi-eventos.dias-permitidos debe tener al menos un día");
        }
        EnumSet<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        for (String parte : valor.split(",")) {
            String nombre = parte.trim().toUpperCase(Locale.ROOT);
            if (nombre.isEmpty()) {
                continue;
            }
            try {
                dias.add(DayOfWeek.valueOf(nombre));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("maxi-eventos.dias-permitidos tiene un día que no existe: '" + parte.trim()
                        + "' (valores válidos: " + Arrays.toString(DayOfWeek.values()) + ")", ex);
            }
        }
        return new DiasPermitidos(dias);
    }

    public boolean permite(LocalDate fecha) {
        return dias.contains(fecha.getDayOfWeek());
    }

    public Set<DayOfWeek> dias() {
        return dias;
    }

    /** "Solo se pueden registrar eventos en viernes, sábado o domingo." */
    public String mensaje() {
        return mensaje;
    }

    private String enumerar() {
        List<String> nombres = dias.stream().map(d -> NOMBRES.get(d.getValue() - 1)).toList();
        if (nombres.size() == 1) {
            return nombres.get(0);
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " o " + nombres.get(nombres.size() - 1);
    }
}

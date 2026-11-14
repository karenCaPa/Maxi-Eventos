package com.maxieventos.eventos.service;

import com.maxieventos.eventos.entity.FechaAgenda;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Regla de negocio: solo puede haber un evento por fin de semana.
 * <ul>
 *   <li>Viernes, sábado y domingo forman un mismo fin de semana: si cualquiera de los otros dos días ya está
 *       PENDIENTE u OCUPADO, la fecha no se puede usar (también cruzando de mes o de año).</li>
 *   <li>De lunes a jueves solo se valida que la propia fecha esté libre (lo hace quien llama). Desde la 10B3 esos días
 *       no se pueden usar salvo que se configuren en {@link DiasPermitidos} (por defecto solo viernes, sábado y domingo).</li>
 * </ul>
 * Una fecha DISPONIBLE (por ejemplo, la de un evento cancelado) no bloquea.
 */
public final class ReglaFinDeSemana {

    private ReglaFinDeSemana() {
    }

    public static boolean esFinDeSemana(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia == DayOfWeek.FRIDAY || dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }

    /** Los otros días del mismo fin de semana (vacío de lunes a jueves). */
    public static List<LocalDate> otrosDiasDelFinDeSemana(LocalDate fecha) {
        if (!esFinDeSemana(fecha)) {
            return List.of();
        }
        LocalDate viernes = fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        return List.of(viernes, viernes.plusDays(1), viernes.plusDays(2)).stream()
                .filter(dia -> !dia.equals(fecha))
                .toList();
    }

    /**
     * Devuelve la fecha del mismo fin de semana que ya está tomada (PENDIENTE u OCUPADA), si existe.
     *
     * @param registros estados de agenda conocidos (pueden incluir otras fechas; se ignoran)
     */
    public static Optional<FechaAgenda> conflicto(LocalDate fecha, Collection<FechaAgenda> registros) {
        List<LocalDate> otros = otrosDiasDelFinDeSemana(fecha);
        return registros.stream()
                .filter(registro -> otros.contains(registro.getFecha()))
                .filter(registro -> !registro.estaLibre())
                .findFirst();
    }
}

package com.maxieventos.eventos.dto;

import com.maxieventos.eventos.entity.EstadoFecha;
import com.maxieventos.eventos.entity.FechaAgenda;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;

/** Estado de una fecha de la agenda; {@code diaSemana} en español (por ejemplo, "sábado"). */
public record FechaResponse(LocalDate fecha, String diaSemana, EstadoFecha estado, UUID eventoId) {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    public static FechaResponse from(FechaAgenda agenda) {
        return new FechaResponse(agenda.getFecha(), dia(agenda.getFecha().getDayOfWeek()), agenda.getEstado(),
                agenda.getEventoId());
    }

    public static FechaResponse disponible(LocalDate fecha) {
        return new FechaResponse(fecha, dia(fecha.getDayOfWeek()), EstadoFecha.DISPONIBLE, null);
    }

    private static String dia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ESPANOL);
    }
}

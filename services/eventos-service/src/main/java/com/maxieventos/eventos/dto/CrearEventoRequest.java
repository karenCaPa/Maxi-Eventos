package com.maxieventos.eventos.dto;

import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.entity.ZonaEvento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CrearEventoRequest(
        @NotBlank @Size(min = 3, max = 150) String nombreEvento,
        @NotNull UUID clienteId,
        /** Debe ser posterior a hoy (en hora de Colombia); lo valida el service con el reloj del negocio. */
        @NotNull LocalDate fecha,
        @NotNull TipoEvento tipoEvento,
        @NotNull @PositiveOrZero @Max(5000) Integer cantidadAdultos,
        @NotNull @PositiveOrZero @Max(5000) Integer cantidadNinos,
        @NotNull ZonaEvento zona,
        UUID lugarId) {
}

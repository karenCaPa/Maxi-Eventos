package com.maxieventos.eventos.dto;

import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.Evento;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.entity.ZonaEvento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String nombreEvento,
        LocalDate fecha,
        UUID clienteId,
        TipoEvento tipoEvento,
        int cantidadAdultos,
        int cantidadNinos,
        int numInvitados,
        ZonaEvento zona,
        UUID lugarId,
        EstadoEvento estado,
        Instant fechaCreacion) {

    public static EventoResponse from(Evento evento) {
        return new EventoResponse(evento.getId(), evento.getNombreEvento(), evento.getFecha(), evento.getClienteId(),
                evento.getTipoEvento(), evento.getCantidadAdultos(), evento.getCantidadNinos(), evento.getNumInvitados(),
                evento.getZona(), evento.getLugarId(), evento.getEstado(), evento.getFechaCreacion());
    }
}

package com.maxieventos.eventos.controller;

import com.maxieventos.eventos.dto.AsignarLugarRequest;
import com.maxieventos.eventos.dto.CrearEventoRequest;
import com.maxieventos.eventos.dto.EventoResponse;
import com.maxieventos.eventos.dto.PaginaResponse;
import com.maxieventos.eventos.entity.EstadoEvento;
import com.maxieventos.eventos.entity.TipoEvento;
import com.maxieventos.eventos.service.EventoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/eventos")
public class EventoController {
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fecha", "fechaCreacion", "tipoEvento", "estado",
            "nombreEvento", "numInvitados");

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @PostMapping
    public ResponseEntity<EventoResponse> crear(@Valid @RequestBody CrearEventoRequest request) {
        EventoResponse creado = eventoService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    /** También lo consulta proveedores-service para validar que el evento exista (solo mira 200 / 404). */
    @GetMapping("/{id}")
    public EventoResponse obtener(@PathVariable UUID id) {
        return eventoService.obtener(id);
    }

    @GetMapping
    public PaginaResponse<EventoResponse> listar(
            @RequestParam(required = false) EstadoEvento estado,
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return eventoService.listar(estado, tipo, desde, hasta,
                Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by("fecha")));
    }

    @PatchMapping("/{id}/confirmar")
    public EventoResponse confirmar(@PathVariable UUID id) {
        return eventoService.confirmar(id);
    }

    @PatchMapping("/{id}/cancelar")
    public EventoResponse cancelar(@PathVariable UUID id) {
        return eventoService.cancelar(id);
    }

    @PatchMapping("/{id}/lugar")
    public EventoResponse asignarLugar(@PathVariable UUID id, @Valid @RequestBody AsignarLugarRequest request) {
        return eventoService.asignarLugar(id, request.lugarId());
    }
}

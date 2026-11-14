package com.maxieventos.eventos.controller;

import com.maxieventos.eventos.dto.EstadoFechaRequest;
import com.maxieventos.eventos.dto.FechaResponse;
import com.maxieventos.eventos.service.FechaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fechas")
public class FechaController {
    private final FechaService fechaService;

    public FechaController(FechaService fechaService) {
        this.fechaService = fechaService;
    }

    /** Fechas en las que se puede crear un evento (lo usa el gateway GraphQL para fechasDisponibles). */
    @GetMapping("/disponibles")
    public List<FechaResponse> disponibles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return fechaService.disponibles(desde, hasta);
    }

    /** Fechas registradas en la agenda (con evento o bloqueo) dentro del rango. */
    @GetMapping
    public List<FechaResponse> agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return fechaService.agenda(desde, hasta);
    }

    @PutMapping("/{fecha}/estado")
    public FechaResponse actualizarEstado(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @Valid @RequestBody EstadoFechaRequest request) {
        return fechaService.actualizarEstado(fecha, request.estado());
    }
}

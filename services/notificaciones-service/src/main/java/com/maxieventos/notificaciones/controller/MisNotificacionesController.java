package com.maxieventos.notificaciones.controller;

import com.maxieventos.notificaciones.dto.MarcadasResponse;
import com.maxieventos.notificaciones.dto.MiNotificacionResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.dto.SinLeerResponse;
import com.maxieventos.notificaciones.service.LecturaNotificacionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fase 11H: la campanita de la interfaz. El usuario es el {@code sub} del JWT (lo pone {@code JwtAuthenticationFilter}
 * como principal); las lecturas son por usuario. Lo pueden usar los dos roles (cualquier token válido).
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class MisNotificacionesController {
    private final LecturaNotificacionService servicio;

    public MisNotificacionesController(LecturaNotificacionService servicio) {
        this.servicio = servicio;
    }

    /** Las notificaciones de todos los eventos, más recientes primero, con si el usuario actual la leyó. */
    @GetMapping("/recientes")
    public PaginaResponse<MiNotificacionResponse> recientes(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            Principal usuario) {
        return servicio.recientes(usuario.getName(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaCreacion").and(Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/sin-leer")
    public SinLeerResponse sinLeer(Principal usuario) {
        return servicio.sinLeer(usuario.getName());
    }

    /** Idempotente. Inexistente → 404 con ErrorResponse. */
    @PatchMapping("/{id}/leida")
    public MiNotificacionResponse marcarLeida(@PathVariable UUID id, Principal usuario) {
        return servicio.marcarLeida(usuario.getName(), id);
    }

    @PatchMapping("/leidas")
    public MarcadasResponse marcarTodas(Principal usuario) {
        return servicio.marcarTodas(usuario.getName());
    }
}

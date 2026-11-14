package com.maxieventos.notificaciones.controller;

import com.maxieventos.notificaciones.dto.NotificacionResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.service.NotificacionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificacionController {
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fechaCreacion", "tipo");

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /** Notificaciones generadas, de la más reciente a la más antigua por defecto. Filtro opcional por evento. */
    @GetMapping("/notificaciones")
    public PaginaResponse<NotificacionResponse> listar(
            @RequestParam(required = false) UUID eventoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return notificacionService.listar(eventoId,
                Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by(Sort.Direction.DESC, "fechaCreacion")));
    }
}

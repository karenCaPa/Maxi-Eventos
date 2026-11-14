package com.maxieventos.auditoria.controller;

import com.maxieventos.auditoria.dto.AuditoriaEventoResponse;
import com.maxieventos.auditoria.dto.PaginaResponse;
import com.maxieventos.auditoria.entity.TipoMensaje;
import com.maxieventos.auditoria.service.AuditoriaService;
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
public class AuditoriaController {
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fechaRecepcion", "fechaOcurrencia", "tipo");

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /** Mensajes Kafka recibidos, del más reciente al más antiguo por defecto. Filtros opcionales por evento y tipo. */
    @GetMapping("/auditoria")
    public PaginaResponse<AuditoriaEventoResponse> listar(
            @RequestParam(required = false) UUID eventoId,
            @RequestParam(required = false) TipoMensaje tipo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return auditoriaService.listar(eventoId, tipo,
                Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by(Sort.Direction.DESC, "fechaRecepcion")));
    }
}

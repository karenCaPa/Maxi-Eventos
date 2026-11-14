package com.maxieventos.logistica.controller;

import com.maxieventos.logistica.dto.ActualizarItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraRequest;
import com.maxieventos.logistica.dto.ItemCompraResponseDTO;
import com.maxieventos.logistica.dto.PaginaResponse;
import com.maxieventos.logistica.dto.RequerimientosLogistica;

import com.maxieventos.logistica.service.LogisticaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/eventos/{eventoId}")
public class LogisticaController {
    private static final Set<String> CAMPOS_COMPRA = Set.of("id", "nombreItem", "comprado");

    private final LogisticaService logisticaService;

    public LogisticaController(LogisticaService logisticaService) {
        this.logisticaService = logisticaService;
    }

    @GetMapping("/logistica")
    public RequerimientosLogistica obtenerLogistica(@PathVariable UUID eventoId) {
        return logisticaService.obtenerRequerimientos(eventoId);
    }

    @GetMapping("/compras")
    public PaginaResponse<ItemCompraResponseDTO> obtenerCompras(
            @PathVariable UUID eventoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return logisticaService.obtenerCompras(eventoId,
                Paginacion.crear(page, size, sort, CAMPOS_COMPRA, Sort.by("nombreItem")));
    }

    /** Devuelve la página de compras ya actualizada (mismos parámetros que el GET). Un ítem de otro evento → 404. */
    @PutMapping("/compras")
    public PaginaResponse<ItemCompraResponseDTO> actualizarCompras(
            @PathVariable UUID eventoId,
            @Valid @RequestBody @NotEmpty List<@Valid ActualizarItemCompraRequest> solicitudes,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return logisticaService.actualizarCompras(eventoId, solicitudes,
                Paginacion.crear(page, size, sort, CAMPOS_COMPRA, Sort.by("nombreItem")));
    }

    /** 10B3: agrega un ítem MANUAL (201 + Location). 400 por campo; 404 sin logística; 409 nombre repetido en el evento. */
    @PostMapping("/compras")
    public ResponseEntity<ItemCompraResponseDTO> agregarItem(@PathVariable UUID eventoId,
                                                             @Valid @RequestBody ItemCompraRequest request) {
        ItemCompraResponseDTO creado = logisticaService.agregarItem(eventoId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    /** 10B3: edita nombre, cantidad y nota (calculado o manual); conserva el origen y la marca de comprado. */
    @PutMapping("/compras/{itemId}")
    public ItemCompraResponseDTO editarItem(@PathVariable UUID eventoId, @PathVariable UUID itemId,
                                            @Valid @RequestBody ItemCompraRequest request) {
        return logisticaService.editarItem(eventoId, itemId, request);
    }

    /** 10B3: quita un ítem MANUAL (204). Calculado → 409; ítem de otro evento o evento sin logística → 404. */
    @DeleteMapping("/compras/{itemId}")
    public ResponseEntity<Void> quitarItem(@PathVariable UUID eventoId, @PathVariable UUID itemId) {
        logisticaService.quitarItem(eventoId, itemId);
        return ResponseEntity.noContent().build();
    }
}

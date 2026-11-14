package com.maxieventos.proveedores.controller;

import com.maxieventos.proveedores.dto.EstadoAsignacionRequest;
import com.maxieventos.proveedores.dto.EstadoProveedorRequest;
import com.maxieventos.proveedores.dto.EventoProveedorRequest;
import com.maxieventos.proveedores.dto.EventoProveedorResponseDTO;
import com.maxieventos.proveedores.dto.PaginaResponse;
import com.maxieventos.proveedores.dto.ProveedorRequest;
import com.maxieventos.proveedores.dto.ProveedorResponseDTO;
import com.maxieventos.proveedores.service.ProveedorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/proveedores")
public class ProveedorController {
    private static final Set<String> CAMPOS_PROVEEDOR = Set.of("id", "nombre", "categoria", "contacto", "activo");
    private static final Set<String> CAMPOS_ASIGNACION = Set.of("id", "servicioAsignado", "costoAcordado", "estado");

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    @GetMapping
    public PaginaResponse<ProveedorResponseDTO> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return proveedorService.listar(categoria, activo,
                Paginacion.crear(page, size, sort, CAMPOS_PROVEEDOR, Sort.by("nombre")));
    }

    @GetMapping("/{id}")
    public ProveedorResponseDTO obtener(@PathVariable UUID id) {
        return proveedorService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ProveedorResponseDTO> crear(@Valid @RequestBody ProveedorRequest request) {
        ProveedorResponseDTO creado = proveedorService.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public ProveedorResponseDTO actualizar(@PathVariable UUID id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    public ProveedorResponseDTO cambiarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoProveedorRequest request) {
        return proveedorService.cambiarEstado(id, request.activo());
    }

    /** 204 si el proveedor no tiene asignaciones; 409 si las tiene (se debe inactivar). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        proveedorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** 404 si el evento no existe; página vacía si existe y no tiene asignaciones; 503 si Eventos no responde. */
    @GetMapping("/eventos/{eventoId}")
    public PaginaResponse<EventoProveedorResponseDTO> listarPorEvento(
            @RequestHeader("Authorization") String authorization,
            @PathVariable UUID eventoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return proveedorService.listarAsignaciones(eventoId,
                Paginacion.crear(page, size, sort, CAMPOS_ASIGNACION, Sort.by("servicioAsignado")), authorization);
    }

    @PostMapping("/eventos")
    public ResponseEntity<EventoProveedorResponseDTO> asignarProveedor(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody EventoProveedorRequest request) {
        EventoProveedorResponseDTO asignacion = proveedorService.asignar(request, authorization);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{eventoId}")
                .buildAndExpand(asignacion.eventoId()).toUri();
        return ResponseEntity.created(ubicacion).body(asignacion);
    }

    /** Confirma una asignación pendiente. 404 si no existe; 409 si no está PENDIENTE o el estado pedido no es CONFIRMADO. */
    @PatchMapping("/eventos/asignaciones/{asignacionId}/estado")
    public EventoProveedorResponseDTO cambiarEstadoAsignacion(@PathVariable UUID asignacionId,
                                                              @Valid @RequestBody EstadoAsignacionRequest request) {
        return proveedorService.cambiarEstadoAsignacion(asignacionId, request.estado());
    }
}

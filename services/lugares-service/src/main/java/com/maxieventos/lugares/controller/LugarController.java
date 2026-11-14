package com.maxieventos.lugares.controller;

import com.maxieventos.lugares.dto.EstadoLugarRequest;
import com.maxieventos.lugares.dto.LugarRequest;
import com.maxieventos.lugares.dto.LugarResponseDTO;
import com.maxieventos.lugares.dto.PaginaResponse;
import com.maxieventos.lugares.entity.EstadoLugar;
import com.maxieventos.lugares.service.LugarService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/lugares")
public class LugarController {

    private static final Set<String> CAMPOS_ORDENABLES = Set.of("id", "nombre", "direccion", "tipo", "capacidad", "estado");

    private final LugarService service;

    public LugarController(LugarService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<LugarResponseDTO> listar(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) EstadoLugar estado,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return service.listar(nombre, tipo, estado,
                Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by("nombre")));
    }

    @PostMapping
    public ResponseEntity<LugarResponseDTO> crear(@Valid @RequestBody LugarRequest request) {
        LugarResponseDTO creado = service.crear(request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @GetMapping("/{id}")
    public LugarResponseDTO obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    public LugarResponseDTO actualizar(@PathVariable UUID id, @Valid @RequestBody LugarRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    public LugarResponseDTO cambiarEstado(@PathVariable UUID id, @Valid @RequestBody EstadoLugarRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

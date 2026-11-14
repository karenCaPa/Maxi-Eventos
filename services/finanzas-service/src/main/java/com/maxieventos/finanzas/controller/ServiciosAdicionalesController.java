package com.maxieventos.finanzas.controller;

import com.maxieventos.finanzas.dto.AdicionalCatalogoResponse;
import com.maxieventos.finanzas.dto.AdicionalEventoResponseDTO;
import com.maxieventos.finanzas.dto.AdicionalRequest;
import com.maxieventos.finanzas.service.ServiciosAdicionalesService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class ServiciosAdicionalesController {
    private final ServiciosAdicionalesService service;

    public ServiciosAdicionalesController(ServiciosAdicionalesService service) {
        this.service = service;
    }

    @GetMapping("/servicios-adicionales")
    public List<AdicionalCatalogoResponse> catalogo() {
        return service.catalogo();
    }

    /** 404 si el evento no existe; lista vacía si no tiene adicionales; 503 si eventos-service no responde. */
    @GetMapping("/eventos/{eventoId}/adicionales")
    public List<AdicionalEventoResponseDTO> listar(@PathVariable UUID eventoId) {
        return service.listar(eventoId);
    }

    /** Location apunta a los adicionales del evento ({@code GET /eventos/{eventoId}/adicionales}). */
    @PostMapping("/eventos/{eventoId}/adicionales")
    public ResponseEntity<AdicionalEventoResponseDTO> agregar(@PathVariable UUID eventoId,
                                                            @Valid @RequestBody AdicionalRequest request) {
        AdicionalEventoResponseDTO adicional = service.agregar(eventoId, request);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(ubicacion).body(adicional);
    }
}

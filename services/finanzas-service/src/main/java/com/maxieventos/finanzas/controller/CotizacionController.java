package com.maxieventos.finanzas.controller;

import com.maxieventos.finanzas.dto.BalanceResponse;
import com.maxieventos.finanzas.dto.CotizacionRequest;
import com.maxieventos.finanzas.dto.CotizacionResponseDTO;
import com.maxieventos.finanzas.dto.ResultadoCreacion;
import com.maxieventos.finanzas.service.CotizacionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class CotizacionController {
    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    /**
     * 201 si la cotización se crea; 200 si el evento ya tenía cotización (se devuelve la existente, no se regenera).
     * Los datos del evento se consultan a eventos-service.
     */
    @PostMapping("/api/v1/cotizaciones")
    public ResponseEntity<CotizacionResponseDTO> crear(@Valid @RequestBody CotizacionRequest request) {
        ResultadoCreacion<CotizacionResponseDTO> resultado = cotizacionService.cotizar(request.eventoId());
        if (!resultado.creado()) {
            return ResponseEntity.ok(resultado.recurso());
        }
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/eventos/{eventoId}/cotizacion").buildAndExpand(request.eventoId()).toUri();
        return ResponseEntity.created(ubicacion).body(resultado.recurso());
    }

    @GetMapping("/api/v1/eventos/{eventoId}/cotizacion")
    public CotizacionResponseDTO obtener(@PathVariable UUID eventoId) {
        return cotizacionService.obtenerCotizacion(eventoId);
    }

    @GetMapping("/api/v1/eventos/{eventoId}/balance")
    public BalanceResponse balance(@PathVariable UUID eventoId) {
        return cotizacionService.obtenerBalance(eventoId);
    }
}

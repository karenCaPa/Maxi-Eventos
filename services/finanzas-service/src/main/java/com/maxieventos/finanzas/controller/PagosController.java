package com.maxieventos.finanzas.controller;

import com.maxieventos.finanzas.dto.PagoRequest;
import com.maxieventos.finanzas.dto.PagoResponse;
import com.maxieventos.finanzas.dto.PaginaResponse;
import com.maxieventos.finanzas.exception.SolicitudInvalidaException;
import com.maxieventos.finanzas.service.PagoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "monto", "metodoPago");
    private static final int LONGITUD_MAXIMA_CLAVE = 100;

    private final PagoService pagoService;

    public PagosController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping("/finanzas/pagos")
    public ResponseEntity<PagoResponse> registrarPago(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            @Valid @RequestBody PagoRequest request) {
        String bodyKey = request.eventIdempotencia() == null ? null : request.eventIdempotencia().toString();
        if (bodyKey != null && idempotencyHeader != null && !bodyKey.equals(idempotencyHeader)) {
            throw new SolicitudInvalidaException("eventIdempotencia",
                    "debe coincidir con la cabecera X-Idempotency-Key");
        }
        String key = bodyKey != null ? bodyKey : idempotencyHeader;
        if (key == null || key.isBlank()) key = UUID.randomUUID().toString();
        if (key.length() > LONGITUD_MAXIMA_CLAVE) {
            throw new SolicitudInvalidaException("X-Idempotency-Key",
                    "no puede tener más de " + LONGITUD_MAXIMA_CLAVE + " caracteres");
        }

        PagoService.RegistroPago registro = pagoService.registrarPago(request, key);
        PagoResponse response = registro.pago();
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Idempotency-Key", key);
        headers.add("X-Already-Processed", Boolean.toString(registro.duplicado()));

        // 201 si el pago es nuevo; 200 si es una repetición idempotente (misma clave) y se devuelve el ya registrado.
        if (registro.duplicado()) {
            return ResponseEntity.ok().headers(headers).body(response);
        }
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/eventos/{eventoId}/pagos").buildAndExpand(request.eventoId()).toUri();
        return ResponseEntity.created(ubicacion).headers(headers).body(response);
    }

    /** 404 si el evento no existe; página vacía si no tiene pagos; 503 si eventos-service no responde. */
    @GetMapping("/eventos/{eventoId}/pagos")
    public PaginaResponse<PagoResponse> listar(
            @PathVariable UUID eventoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return pagoService.listar(eventoId, Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by("creadoEn")));
    }
}

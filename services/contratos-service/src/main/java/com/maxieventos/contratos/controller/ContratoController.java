package com.maxieventos.contratos.controller;

import com.maxieventos.contratos.dto.ContratoResponseDTO;
import com.maxieventos.contratos.dto.PaginaResponse;
import com.maxieventos.contratos.dto.ResultadoCreacion;
import com.maxieventos.contratos.service.ContratosService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class ContratoController {
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("id", "nombreEvento", "estado", "creadoEn");

    private final ContratosService contratosService;

    public ContratoController(ContratosService contratosService) {
        this.contratosService = contratosService;
    }

    @GetMapping("/contratos")
    public PaginaResponse<ContratoResponseDTO> listar(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(Paginacion.TAMANO_MAXIMO) int size,
            @RequestParam(required = false) String sort) {
        return contratosService.listar(
                Paginacion.crear(page, size, sort, CAMPOS_ORDENABLES, Sort.by(Sort.Direction.DESC, "creadoEn")));
    }

    @GetMapping("/contratos/{id}")
    public ContratoResponseDTO obtener(@PathVariable UUID id) {
        return contratosService.obtener(id);
    }

    @GetMapping("/eventos/{eventoId}/contrato")
    public ContratoResponseDTO obtenerDeEvento(@PathVariable UUID eventoId) {
        return contratosService.obtenerDeEvento(eventoId);
    }

    /**
     * 201 si el contrato se crea (PENDIENTE, con los datos de Eventos, Clientes, Lugares y Finanzas); 200 si el evento ya
     * tenía contrato (se devuelve el existente). No necesita cuerpo: si se envía, se ignora.
     */
    @PostMapping("/eventos/{eventoId}/contrato")
    public ResponseEntity<ContratoResponseDTO> crear(@PathVariable UUID eventoId) {
        ResultadoCreacion<ContratoResponseDTO> resultado = contratosService.crear(eventoId);
        if (!resultado.creado()) {
            return ResponseEntity.ok(resultado.recurso());
        }
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/contratos/{id}").buildAndExpand(resultado.recurso().id()).toUri();
        return ResponseEntity.created(ubicacion).body(resultado.recurso());
    }

    @GetMapping("/eventos/{eventoId}/contrato/pdf")
    public ResponseEntity<ByteArrayResource> obtenerPdf(@PathVariable UUID eventoId) {
        return pdf(contratosService.generarPdfContrato(eventoId), "contrato-" + eventoId + ".pdf");
    }

    /** Si el contrato ya está FIRMADO → 409. */
    @PatchMapping("/eventos/{eventoId}/contrato/marcar-firmado")
    public ContratoResponseDTO marcarFirmado(@PathVariable UUID eventoId) {
        return contratosService.marcarFirmado(eventoId);
    }

    /** Anexo con los adicionales de Finanzas; no modifica el contrato. Sin adicionales → 409; Finanzas caído → 503. */
    @GetMapping("/eventos/{eventoId}/adicionales/anexo-pdf")
    public ResponseEntity<ByteArrayResource> obtenerAnexo(@PathVariable UUID eventoId) {
        return pdf(contratosService.generarPdfAnexo(eventoId), "anexo-" + eventoId + ".pdf");
    }

    private static ResponseEntity<ByteArrayResource> pdf(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(new ByteArrayResource(content));
    }
}

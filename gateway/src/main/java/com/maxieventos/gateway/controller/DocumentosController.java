package com.maxieventos.gateway.controller;

import com.maxieventos.gateway.dto.Documento;
import com.maxieventos.gateway.service.remoto.ContratosClient;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descarga de los PDF a través del gateway (Fase 10). GraphQL no sirve bien binarios, así que son dos rutas REST de SOLO
 * LECTURA que reenvían a contratos-service con el mismo token, sin lógica de negocio:
 * <ul>
 *   <li>{@code GET /documentos/eventos/{eventoId}/contrato.pdf} → {@code GET /api/v1/eventos/{id}/contrato/pdf}</li>
 *   <li>{@code GET /documentos/eventos/{eventoId}/anexo.pdf} → {@code GET /api/v1/eventos/{id}/adicionales/anexo-pdf}</li>
 * </ul>
 * El token lo valida antes el {@code JwtAuthenticationFilter} (sin token o inválido → 401 sin llamar a Contratos). Los
 * errores de Contratos (404, 409, 503) se devuelven con el formato {@code ErrorResponse} y el mensaje del servicio
 * ({@code ErroresDocumentos}). El token va siempre en el header, nunca en la URL.
 */
@RestController
@RequestMapping("/documentos/eventos/{eventoId}")
public class DocumentosController {
    private final ContratosClient contratos;

    public DocumentosController(ContratosClient contratos) {
        this.contratos = contratos;
    }

    @GetMapping("/contrato.pdf")
    public ResponseEntity<byte[]> contrato(@PathVariable UUID eventoId,
                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String autorizacion) {
        return pdf(contratos.pdfContrato(autorizacion, eventoId));
    }

    @GetMapping("/anexo.pdf")
    public ResponseEntity<byte[]> anexo(@PathVariable UUID eventoId,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String autorizacion) {
        return pdf(contratos.pdfAnexo(autorizacion, eventoId));
    }

    private static ResponseEntity<byte[]> pdf(Documento documento) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(documento.nombreArchivo()).build().toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(documento.contenido());
    }
}

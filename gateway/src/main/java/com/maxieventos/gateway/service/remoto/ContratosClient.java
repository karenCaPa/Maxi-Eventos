package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.Contrato;
import com.maxieventos.gateway.dto.Documento;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** contratos-service. */
@Component
public class ContratosClient extends ClienteRemoto {

    public ContratosClient(@Qualifier("contratosRestClient") RestClient rest) {
        super(rest, "contratos");
    }

    /** GET /eventos/{id}/contrato; sin contrato → {@code null}. */
    public Contrato contrato(String autorizacion, UUID eventoId) {
        return ejecutarOpcional(rest.get().uri("/api/v1/eventos/{id}/contrato", eventoId), autorizacion, Contrato.class);
    }

    /** POST /eventos/{id}/contrato (201 nuevo o 200 el existente; 409 sin cotización). */
    public Contrato crear(String autorizacion, UUID eventoId) {
        return ejecutar(rest.post().uri("/api/v1/eventos/{id}/contrato", eventoId), autorizacion, Contrato.class);
    }

    /** PATCH /eventos/{id}/contrato/marcar-firmado (409 si ya está FIRMADO). */
    public Contrato firmar(String autorizacion, UUID eventoId) {
        return ejecutar(rest.patch().uri("/api/v1/eventos/{id}/contrato/marcar-firmado", eventoId), autorizacion, Contrato.class);
    }

    /** GET /eventos/{id}/contrato/pdf (404 si el evento no tiene contrato). */
    public Documento pdfContrato(String autorizacion, UUID eventoId) {
        return pdf("/api/v1/eventos/{id}/contrato/pdf", autorizacion, eventoId, "contrato-" + eventoId + ".pdf");
    }

    /** GET /eventos/{id}/adicionales/anexo-pdf (409 si el evento no tiene servicios adicionales). */
    public Documento pdfAnexo(String autorizacion, UUID eventoId) {
        return pdf("/api/v1/eventos/{id}/adicionales/anexo-pdf", autorizacion, eventoId, "anexo-" + eventoId + ".pdf");
    }

    /** Acepta PDF (éxito) y JSON (el ErrorResponse de un 404/409), para conservar el mensaje del servicio. */
    private Documento pdf(String ruta, String autorizacion, UUID eventoId, String nombreArchivo) {
        byte[] contenido = ejecutar(rest.get().uri(ruta, eventoId).accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON),
                autorizacion, byte[].class);
        return new Documento(contenido, nombreArchivo);
    }
}

package com.maxieventos.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.gateway.dto.Documento;
import com.maxieventos.gateway.exception.ErrorRemoto;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import com.maxieventos.gateway.security.TokensDePrueba;
import com.maxieventos.gateway.service.remoto.ContratosClient;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Descarga de los PDF por el gateway: token, reenvío a Contratos, errores con ErrorResponse y CORS (Fase 10). */
@SpringBootTest(properties = {TokensDePrueba.PROP_SECRETO, TokensDePrueba.PROP_CORS})
@AutoConfigureMockMvc
class DocumentosControllerTest {

    private static final String PERMITIDO = "http://localhost:5173";
    private static final String NO_PERMITIDO = "http://sitio-ajeno.example";
    private static final byte[] PDF = "%PDF-1.7 contrato de prueba".getBytes(StandardCharsets.US_ASCII);
    private static final String ACEPTA = "application/pdf, application/json";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContratosClient contratos;

    private final UUID eventoId = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");

    private MockHttpServletRequestBuilder descarga(String archivo, String token) {
        var peticion = get("/documentos/eventos/{id}/" + archivo, eventoId).header("Accept", ACEPTA);
        return token == null ? peticion : peticion.header("Authorization", token);
    }

    @Test
    void contratoConTokenReenviaElTokenYDevuelveElPdfComoAdjunto() throws Exception {
        String token = TokensDePrueba.valido();
        when(contratos.pdfContrato(token, eventoId)).thenReturn(new Documento(PDF, "contrato-" + eventoId + ".pdf"));

        mvc.perform(descarga("contrato.pdf", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"contrato-" + eventoId + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
        verify(contratos).pdfContrato(token, eventoId);
    }

    @Test
    void anexoConTokenDevuelveElPdfDelAnexo() throws Exception {
        when(contratos.pdfAnexo(any(), eq(eventoId))).thenReturn(new Documento(PDF, "anexo-" + eventoId + ".pdf"));

        mvc.perform(descarga("anexo.pdf", TokensDePrueba.valido()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("anexo-" + eventoId + ".pdf")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void sinTokenEs401ConErrorResponseYNoSeLlamaAContratos() throws Exception {
        mvc.perform(descarga("contrato.pdf", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Debes iniciar sesión")))
                .andExpect(jsonPath("$.path").value("/documentos/eventos/" + eventoId + "/contrato.pdf"));
        verifyNoInteractions(contratos);
    }

    @Test
    void tokenVencidoOManipuladoEs401SinLlamarAContratos() throws Exception {
        mvc.perform(descarga("contrato.pdf", TokensDePrueba.vencido()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("venció")));
        mvc.perform(descarga("anexo.pdf", TokensDePrueba.manipulado()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("no es válido")));
        verifyNoInteractions(contratos);
    }

    @Test
    void anexoSinAdicionalesEs409ConElMensajeDeContratos() throws Exception {
        String mensaje = "El evento no tiene servicios adicionales; no hay anexo que generar.";
        when(contratos.pdfAnexo(any(), eq(eventoId))).thenThrow(ServicioRemotoException.deRespuesta("contratos", 409,
                new ErrorRemoto(409, "Conflict", mensaje, "/api/v1/eventos/" + eventoId + "/adicionales/anexo-pdf", List.of())));

        mvc.perform(descarga("anexo.pdf", TokensDePrueba.valido()))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(mensaje))
                .andExpect(jsonPath("$.path").value("/documentos/eventos/" + eventoId + "/anexo.pdf"));
    }

    @Test
    void eventoSinContratoEs404YContratosCaidoEs503() throws Exception {
        when(contratos.pdfContrato(any(), eq(eventoId))).thenThrow(ServicioRemotoException.deRespuesta("contratos", 404,
                new ErrorRemoto(404, "Not Found", "No existe un contrato para el evento " + eventoId, null, List.of())));
        mvc.perform(descarga("contrato.pdf", TokensDePrueba.valido()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe un contrato para el evento " + eventoId));

        when(contratos.pdfAnexo(any(), eq(eventoId)))
                .thenThrow(ServicioRemotoException.noDisponible("contratos", new ConnectException("sin conexión")));
        mvc.perform(descarga("anexo.pdf", TokensDePrueba.valido()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("contratos no está disponible")));
    }

    @Test
    void idQueNoEsUuidEs400SinLlamarAContratos() throws Exception {
        mvc.perform(get("/documentos/eventos/abc/contrato.pdf").header("Authorization", TokensDePrueba.valido()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El identificador del evento no es un UUID válido."));
        verifyNoInteractions(contratos);
    }

    @Test
    void preflightDesdeElOrigenDeLaInterfazPermiteGetConAuthorization() throws Exception {
        mvc.perform(options("/documentos/eventos/{id}/contrato.pdf", eventoId).header("Origin", PERMITIDO)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization, accept"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(contratos);
    }

    @Test
    void preflightDesdeOtroOrigenOConOtroMetodoEsRechazado() throws Exception {
        mvc.perform(options("/documentos/eventos/{id}/contrato.pdf", eventoId).header("Origin", NO_PERMITIDO)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/documentos/eventos/{id}/contrato.pdf", eventoId).header("Origin", PERMITIDO)
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void laDescargaRealExponeContentDispositionAlOrigenPermitido() throws Exception {
        when(contratos.pdfContrato(any(), eq(eventoId))).thenReturn(new Documento(PDF, "contrato-" + eventoId + ".pdf"));

        mvc.perform(descarga("contrato.pdf", TokensDePrueba.valido()).header("Origin", PERMITIDO))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PERMITIDO))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Content-Disposition")));
    }
}

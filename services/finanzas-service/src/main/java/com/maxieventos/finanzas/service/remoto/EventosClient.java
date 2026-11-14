package com.maxieventos.finanzas.service.remoto;

import com.maxieventos.finanzas.dto.remoto.EventoRemoto;
import com.maxieventos.finanzas.exception.RecursoNoEncontradoException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EventosClient {
    private final RestClient restClient;

    public EventosClient(@Qualifier("eventosRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** El evento; 404 si no existe, 503 si eventos-service no responde. */
    public EventoRemoto obtener(UUID eventoId) {
        return LlamadaRemota.obtener(restClient, "eventos", EventoRemoto.class, "/api/v1/eventos/{id}", eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un evento con id " + eventoId));
    }
}

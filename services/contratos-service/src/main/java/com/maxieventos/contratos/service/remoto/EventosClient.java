package com.maxieventos.contratos.service.remoto;

import com.maxieventos.contratos.dto.remoto.EventoRemoto;
import java.util.Optional;
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

    public Optional<EventoRemoto> obtener(UUID eventoId) {
        return LlamadaRemota.obtener(restClient, "eventos", EventoRemoto.class, "/api/v1/eventos/{id}", eventoId);
    }
}

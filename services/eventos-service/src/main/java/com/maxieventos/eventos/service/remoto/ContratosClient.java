package com.maxieventos.eventos.service.remoto;

import com.maxieventos.eventos.dto.remoto.ContratoRemoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContratosClient {
    private final RestClient restClient;

    public ContratosClient(@Qualifier("contratosRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<ContratoRemoto> obtenerDeEvento(UUID eventoId) {
        return LlamadaRemota.obtener(restClient, "contratos", ContratoRemoto.class,
                "/api/v1/eventos/{eventoId}/contrato", eventoId);
    }
}

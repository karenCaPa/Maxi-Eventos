package com.maxieventos.eventos.service.remoto;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClientesClient {
    private final RestClient restClient;

    public ClientesClient(@Qualifier("clientesRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public boolean existe(UUID clienteId) {
        return LlamadaRemota.obtener(restClient, "clientes", Map.class, "/api/v1/clientes/{id}", clienteId).isPresent();
    }
}

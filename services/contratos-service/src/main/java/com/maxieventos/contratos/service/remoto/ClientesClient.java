package com.maxieventos.contratos.service.remoto;

import com.maxieventos.contratos.dto.remoto.ClienteRemoto;
import java.util.Optional;
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

    public Optional<ClienteRemoto> obtener(UUID clienteId) {
        return LlamadaRemota.obtener(restClient, "clientes", ClienteRemoto.class, "/api/v1/clientes/{id}", clienteId);
    }
}

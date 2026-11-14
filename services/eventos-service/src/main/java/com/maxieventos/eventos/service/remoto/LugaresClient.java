package com.maxieventos.eventos.service.remoto;

import com.maxieventos.eventos.dto.remoto.LugarRemoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LugaresClient {
    private final RestClient restClient;

    public LugaresClient(@Qualifier("lugaresRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<LugarRemoto> obtener(UUID lugarId) {
        return LlamadaRemota.obtener(restClient, "lugares", LugarRemoto.class, "/api/v1/lugares/{id}", lugarId);
    }
}

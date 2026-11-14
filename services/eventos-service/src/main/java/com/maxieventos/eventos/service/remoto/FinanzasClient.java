package com.maxieventos.eventos.service.remoto;

import com.maxieventos.eventos.dto.remoto.BalanceRemoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FinanzasClient {
    private final RestClient restClient;

    public FinanzasClient(@Qualifier("finanzasRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** Vacío si el evento no tiene cotización (Finanzas responde 404). */
    public Optional<BalanceRemoto> obtenerBalance(UUID eventoId) {
        return LlamadaRemota.obtener(restClient, "finanzas", BalanceRemoto.class,
                "/api/v1/eventos/{eventoId}/balance", eventoId);
    }
}

package com.maxieventos.contratos.service.remoto;

import com.maxieventos.contratos.dto.remoto.CotizacionRemota;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta a finanzas-service el total de la cotización (foto del contrato). Desde la Fase 5B los adicionales del anexo
 * ya no se consultan aquí: llegan por Kafka (SERVICIO_ADICIONAL_AGREGADO) y se guardan en contrato_adicionales.
 */
@Component
public class FinanzasClient {
    private final RestClient restClient;

    public FinanzasClient(@Qualifier("finanzasRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<CotizacionRemota> cotizacion(UUID eventoId) {
        return LlamadaRemota.obtener(restClient, "finanzas", CotizacionRemota.class,
                "/api/v1/eventos/{eventoId}/cotizacion", eventoId);
    }
}

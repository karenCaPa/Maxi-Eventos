package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.RegistroAuditoria;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** auditoria-service. */
@Component
public class AuditoriaClient extends ClienteRemoto {
    private static final ParameterizedTypeReference<Pagina<RegistroAuditoria>> PAGINA = new ParameterizedTypeReference<>() { };

    public AuditoriaClient(@Qualifier("auditoriaRestClient") RestClient rest) {
        super(rest, "auditoria");
    }

    public Pagina<RegistroAuditoria> deEvento(String autorizacion, UUID eventoId, String tipo, int page, int size) {
        return ejecutar(rest.get().uri(b -> b.path("/api/v1/auditoria")
                .queryParam("eventoId", eventoId)
                .queryParamIfPresent("tipo", Optional.ofNullable(tipo))
                .queryParam("page", page)
                .queryParam("size", size)
                .build()), autorizacion, PAGINA);
    }
}

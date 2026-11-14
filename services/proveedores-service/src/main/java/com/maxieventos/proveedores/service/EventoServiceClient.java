package com.maxieventos.proveedores.service;

import com.maxieventos.proveedores.config.ReferenciaPeticionFilter;
import com.maxieventos.proveedores.exception.RecursoNoEncontradoException;
import com.maxieventos.proveedores.exception.ServicioNoDisponibleException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;

@Component
public class EventoServiceClient {
    private final RestClient restClient;

    public EventoServiceClient(RestClient.Builder builder,
                               @Value("${services.eventos.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
    }

    public void validarEvento(UUID eventoId, String authorization) {
        try {
            restClient.get().uri("/api/v1/eventos/{eventoId}", eventoId)
                    .header("Authorization", authorization)
                    // Fase 11C: el código de referencia de la petición en curso viaja a Eventos
                    .headers(h -> {
                        String referencia = MDC.get(ReferenciaPeticionFilter.MDC_CLAVE);
                        if (referencia != null) {
                            h.set(ReferenciaPeticionFilter.CABECERA, referencia);
                        }
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (request, response) -> {
                                throw new RecursoNoEncontradoException("No existe un evento con id " + eventoId);
                            })
                    .toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No se pudo validar el evento: el token no fue aceptado por el servicio de eventos", exception);
        } catch (org.springframework.web.client.ResourceAccessException
                 | org.springframework.web.client.HttpServerErrorException exception) {
            throw new ServicioNoDisponibleException(
                    "El servicio de eventos no está disponible en este momento. Intenta de nuevo más tarde.", exception);
        }
    }
}

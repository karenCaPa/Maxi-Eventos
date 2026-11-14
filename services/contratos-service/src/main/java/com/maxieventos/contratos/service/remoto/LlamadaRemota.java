package com.maxieventos.contratos.service.remoto;

import com.maxieventos.contratos.exception.ServicioNoDisponibleException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * GET a otro microservicio con traducción uniforme de errores (igual que en eventos-service):
 * 404 → {@link Optional#empty()}; 401 → 401; 5xx, timeout o servicio caído → 503; otro 4xx → error interno (500).
 */
final class LlamadaRemota {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlamadaRemota.class);

    private LlamadaRemota() {
    }

    static <T> Optional<T> obtener(RestClient cliente, String servicio, Class<T> tipo, String uri, Object... variables) {
        return obtener(cliente, servicio, ParameterizedTypeReference.forType(tipo), uri, variables);
    }

    static <T> Optional<T> obtener(RestClient cliente, String servicio, ParameterizedTypeReference<T> tipo, String uri,
                                   Object... variables) {
        try {
            return cliente.get().uri(uri, variables).exchange((peticion, respuesta) -> {
                int codigo = respuesta.getStatusCode().value();
                if (codigo == 404) {
                    return Optional.<T>empty();
                }
                if (codigo == 401) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                            "El servicio de " + servicio + " no aceptó el token. Inicia sesión de nuevo.");
                }
                if (respuesta.getStatusCode().is5xxServerError()) {
                    LOGGER.warn("El servicio de {} respondió {} en {}", servicio, codigo, peticion.getURI().getPath());
                    throw noDisponible(servicio, null);
                }
                if (respuesta.getStatusCode().isError()) {
                    throw new IllegalStateException("Respuesta inesperada " + codigo + " del servicio de " + servicio);
                }
                return Optional.ofNullable(respuesta.bodyTo(tipo));
            });
        } catch (ResourceAccessException ex) {
            LOGGER.warn("El servicio de {} no responde: {}", servicio, ex.getMessage());
            throw noDisponible(servicio, ex);
        }
    }

    private static ServicioNoDisponibleException noDisponible(String servicio, Throwable causa) {
        return new ServicioNoDisponibleException(
                "El servicio de " + servicio + " no está disponible en este momento. Intenta de nuevo más tarde.", causa);
    }
}

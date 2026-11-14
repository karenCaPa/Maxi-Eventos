package com.maxieventos.gateway.service.remoto;

import com.maxieventos.gateway.exception.ErrorRemoto;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Base de los clientes REST del gateway: reenvía el header Authorization recibido y traduce las respuestas de error en
 * {@link ServicioRemotoException} (con el mensaje y el detalle por campo del servicio). Sin lógica de negocio.
 */
abstract class ClienteRemoto {
    protected final RestClient rest;
    protected final String servicio;

    protected ClienteRemoto(RestClient rest, String servicio) {
        this.rest = rest;
        this.servicio = servicio;
    }

    /** Ejecuta la petición; cualquier error (incluido 404) es una excepción. */
    protected <T> T ejecutar(RestClient.RequestHeadersSpec<?> peticion, String autorizacion, ParameterizedTypeReference<T> tipo) {
        return llamar(peticion, autorizacion, tipo, false);
    }

    /** Como {@link #ejecutar}, pero un 404 devuelve {@code null}: el dato aún no existe (contrato, cotización, logística…). */
    protected <T> T ejecutarOpcional(RestClient.RequestHeadersSpec<?> peticion, String autorizacion,
                                     ParameterizedTypeReference<T> tipo) {
        return llamar(peticion, autorizacion, tipo, true);
    }

    protected <T> T ejecutar(RestClient.RequestHeadersSpec<?> peticion, String autorizacion, Class<T> tipo) {
        return ejecutar(peticion, autorizacion, ParameterizedTypeReference.forType(tipo));
    }

    protected <T> T ejecutarOpcional(RestClient.RequestHeadersSpec<?> peticion, String autorizacion, Class<T> tipo) {
        return ejecutarOpcional(peticion, autorizacion, ParameterizedTypeReference.forType(tipo));
    }

    private <T> T llamar(RestClient.RequestHeadersSpec<?> peticion, String autorizacion, ParameterizedTypeReference<T> tipo,
                         boolean nuloSi404) {
        if (autorizacion != null && !autorizacion.isBlank()) {
            peticion.header(HttpHeaders.AUTHORIZATION, autorizacion);
        }
        try {
            return peticion.exchange((solicitud, respuesta) -> {
                int codigo = respuesta.getStatusCode().value();
                if (codigo == 404 && nuloSi404) {
                    return null;
                }
                if (respuesta.getStatusCode().isError()) {
                    throw ServicioRemotoException.deRespuesta(servicio, codigo, leerError(respuesta));
                }
                if (codigo == 204) {
                    return null;
                }
                return respuesta.bodyTo(tipo);
            });
        } catch (ResourceAccessException ex) {
            throw ServicioRemotoException.noDisponible(servicio, ex);
        }
    }

    private static ErrorRemoto leerError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse respuesta) {
        try {
            return respuesta.bodyTo(ErrorRemoto.class);
        } catch (RuntimeException ex) {
            return null; // cuerpo vacío (401 de Spring Security) o no JSON
        }
    }
}

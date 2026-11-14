package com.maxieventos.gateway.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Un {@link RestClient} por servicio: URL por variable de entorno, los mismos timeouts que eventos-service y una línea de
 * log por llamada ({@code REST -> <servicio> <MÉTODO> <ruta> <estado> (<ms> ms)}, logger {@code maxieventos.rest}): con
 * ella se comprueba que una query solo llama a los servicios de los campos pedidos. El header Authorization lo añade
 * cada cliente desde el {@code GraphQLContext} (ver {@link TokenInterceptor}).
 */
@Configuration
public class RestClientConfig {

    private static final Logger LOG_REST = LoggerFactory.getLogger("maxieventos.rest");

    private final RestClient.Builder builder;
    private final Duration conexion;
    private final Duration lectura;

    public RestClientConfig(RestClient.Builder builder, @Value("${servicios.timeout.conexion}") Duration conexion,
                            @Value("${servicios.timeout.lectura}") Duration lectura) {
        this.builder = builder;
        this.conexion = conexion;
        this.lectura = lectura;
    }

    @Bean
    RestClient authRestClient(@Value("${servicios.auth.url}") String url) { return crear("auth", url); }

    @Bean
    RestClient eventosRestClient(@Value("${servicios.eventos.url}") String url) { return crear("eventos", url); }

    @Bean
    RestClient clientesRestClient(@Value("${servicios.clientes.url}") String url) { return crear("clientes", url); }

    @Bean
    RestClient lugaresRestClient(@Value("${servicios.lugares.url}") String url) { return crear("lugares", url); }

    @Bean
    RestClient finanzasRestClient(@Value("${servicios.finanzas.url}") String url) { return crear("finanzas", url); }

    @Bean
    RestClient contratosRestClient(@Value("${servicios.contratos.url}") String url) { return crear("contratos", url); }

    @Bean
    RestClient proveedoresRestClient(@Value("${servicios.proveedores.url}") String url) { return crear("proveedores", url); }

    @Bean
    RestClient auditoriaRestClient(@Value("${servicios.auditoria.url}") String url) { return crear("auditoria", url); }

    @Bean
    RestClient logisticaRestClient(@Value("${servicios.logistica.url}") String url) { return crear("logistica", url); }

    @Bean
    RestClient notificacionesRestClient(@Value("${servicios.notificaciones.url}") String url) {
        return crear("notificaciones", url);
    }

    /**
     * Fábrica con el {@link HttpClient} del JDK (HTTP/1.1). No se usa {@code SimpleClientHttpRequestFactory}
     * ({@code HttpURLConnection}): no admite PATCH (confirmar, cancelar, firmar, asignar lugar) y, en modo streaming, no deja
     * leer el cuerpo de un 401 a un POST (el mensaje de Auth en un login fallido). Incidencia de la verificación 6B.
     */
    private RestClient crear(String servicio, String url) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(conexion).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(lectura);
        return builder.clone().requestFactory(fabrica).baseUrl(url).requestInterceptor(registrar(servicio)).build();
    }

    static ClientHttpRequestInterceptor registrar(String servicio) {
        return (peticion, cuerpo, ejecucion) -> {
            // Fase 11C: la referencia de la petición original viaja al servicio (que la pone en su propio log)
            String referencia = MDC.get(ReferenciaPeticionFilter.MDC_CLAVE);
            if (referencia != null && !peticion.getHeaders().containsKey(ReferenciaPeticionFilter.CABECERA)) {
                peticion.getHeaders().set(ReferenciaPeticionFilter.CABECERA, referencia);
            }
            long inicio = System.nanoTime();
            String ruta = peticion.getURI().getRawPath()
                    + (peticion.getURI().getRawQuery() == null ? "" : "?" + peticion.getURI().getRawQuery());
            try {
                ClientHttpResponse respuesta = ejecucion.execute(peticion, cuerpo);
                LOG_REST.info("REST -> {} {} {} {} ({} ms)", servicio, peticion.getMethod(), ruta,
                        respuesta.getStatusCode().value(), (System.nanoTime() - inicio) / 1_000_000);
                return respuesta;
            } catch (IOException ex) {
                LOG_REST.warn("REST -> {} {} {} SIN RESPUESTA ({} ms): {}", servicio, peticion.getMethod(), ruta,
                        (System.nanoTime() - inicio) / 1_000_000, ex.getMessage());
                throw ex;
            }
        };
    }
}

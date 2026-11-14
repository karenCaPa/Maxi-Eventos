package com.maxieventos.finanzas.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Cliente REST hacia eventos-service: URL por variable de entorno, timeouts cortos y propagación del header
 * {@code Authorization} de la petición entrante (eventos-service también valida el JWT).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient eventosRestClient(RestClient.Builder builder, @Value("${servicios.eventos.url}") String url,
                                        @Value("${servicios.timeout.conexion}") Duration conexion,
                                        @Value("${servicios.timeout.lectura}") Duration lectura) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexion);
        fabrica.setReadTimeout(lectura);
        return builder.clone().requestFactory(fabrica).baseUrl(url).requestInterceptor(propagarAutorizacion()).build();
    }

    /**
     * Copia el header Authorization de la petición HTTP en curso (si la hay) a la llamada saliente, y también su código de
     * referencia {@code X-Request-Id} (Fase 11C, del MDC): así el error de otro servicio se encuentra con el mismo código.
     */
    static ClientHttpRequestInterceptor propagarAutorizacion() {
        return (request, body, execution) -> {
            String referencia = MDC.get(ReferenciaPeticionFilter.MDC_CLAVE);
            if (referencia != null && !request.getHeaders().containsKey(ReferenciaPeticionFilter.CABECERA)) {
                request.getHeaders().set(ReferenciaPeticionFilter.CABECERA, referencia);
            }
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
                HttpServletRequest actual = atributos.getRequest();
                String autorizacion = actual.getHeader(HttpHeaders.AUTHORIZATION);
                if (autorizacion != null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, autorizacion);
                }
            }
            return execution.execute(request, body);
        };
    }
}

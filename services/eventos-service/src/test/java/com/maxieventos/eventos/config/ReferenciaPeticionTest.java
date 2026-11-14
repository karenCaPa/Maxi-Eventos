package com.maxieventos.eventos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Fase 11C: el código de referencia ({@code X-Request-Id}) que llega del gateway queda en el MDC durante la petición, vuelve
 * en la respuesta y viaja en las llamadas REST de Eventos a otros servicios (junto con el token, como antes).
 */
class ReferenciaPeticionTest {

    private static final String REF = "3f9a12bc-1111-4222-8333-444455556666";

    @AfterEach
    void limpiar() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elFiltroPoneLaReferenciaEnElMdcSoloDuranteLaPeticionYLaDevuelve() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/eventos");
        peticion.addHeader(ReferenciaPeticionFilter.CABECERA, REF);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AtomicReference<String> durante = new AtomicReference<>();

        new ReferenciaPeticionFilter().doFilter(peticion, respuesta,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                        durante.set(MDC.get(ReferenciaPeticionFilter.MDC_CLAVE));
                    }
                });

        assertThat(durante.get()).isEqualTo(REF);
        assertThat(respuesta.getHeader(ReferenciaPeticionFilter.CABECERA)).isEqualTo(REF);
        assertThat(MDC.get(ReferenciaPeticionFilter.MDC_CLAVE)).isNull(); // no se filtra a la siguiente petición del hilo
    }

    @Test
    void lasLlamadasAOtroServicioLlevanLaReferenciaYElToken() {
        MockHttpServletRequest actual = new MockHttpServletRequest();
        actual.addHeader("Authorization", "Bearer token-de-prueba");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(actual));
        MDC.put(ReferenciaPeticionFilter.MDC_CLAVE, REF);

        RestClient.Builder builder = RestClient.builder().requestInterceptor(RestClientConfig.propagarAutorizacion());
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://contratos/api/v1/eventos/1/contrato"))
                .andExpect(header(ReferenciaPeticionFilter.CABECERA, REF))
                .andExpect(header("Authorization", "Bearer token-de-prueba"))
                .andRespond(withSuccess());

        builder.build().get().uri("http://contratos/api/v1/eventos/1/contrato").retrieve().toBodilessEntity();
        servidor.verify();
    }

    @Test
    void fueraDeUnaPeticionNoSeInventaReferencia() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor(RestClientConfig.propagarAutorizacion());
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://contratos/x")).andExpect(headerDoesNotExist(ReferenciaPeticionFilter.CABECERA))
                .andRespond(withSuccess());

        builder.build().get().uri("http://contratos/x").retrieve().toBodilessEntity();
        servidor.verify();
    }
}

package com.maxieventos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maxieventos.gateway.exception.ServicioRemotoException;
import com.maxieventos.gateway.service.remoto.AuthClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Con la fábrica HTTP REAL del gateway (no un mock) contra un servidor HTTP local: PATCH funciona y el cuerpo de un 401 a
 * un POST se lee. Con {@code SimpleClientHttpRequestFactory} las dos cosas fallaban (incidencia de la verificación 6B).
 */
class RestClientConfigTest {

    private HttpServer servidor;
    private String url;
    private volatile String ultimoMetodo;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/eventos", intercambio -> {
            ultimoMetodo = intercambio.getRequestMethod();
            responder(intercambio, 409, "{\"status\":409,\"message\":\"El contrato del evento está en estado PENDIENTE; debe estar FIRMADO.\"}");
        });
        servidor.createContext("/api/v1/auth/login", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            responder(intercambio, 401, "{\"status\":401,\"message\":\"Correo o contraseña incorrectos.\"}");
        });
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private static void responder(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private RestClientConfig config() {
        return new RestClientConfig(RestClient.builder(), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void patchLlegaAlServicioYSuErrorSeTraduce() {
        EventosClient eventos = new EventosClient(config().eventosRestClient(url));

        assertThatThrownBy(() -> eventos.confirmar("Bearer abc", UUID.randomUUID()))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getMessage()).isEqualTo("El contrato del evento está en estado PENDIENTE; debe estar FIRMADO.");
                });
        assertThat(ultimoMetodo).isEqualTo("PATCH");
    }

    @Test
    void el401DeUnPostConservaElMensajeDelServicio() {
        AuthClient auth = new AuthClient(config().authRestClient(url));

        assertThatThrownBy(() -> auth.login("o@e.com", "mala")).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(401);
            assertThat(ex.getMessage()).isEqualTo("Correo o contraseña incorrectos.");
        });
    }

    @Test
    void servicioSinEscucharEsServiceUnavailable() {
        servidor.stop(0);
        EventosClient eventos = new EventosClient(config().eventosRestClient(url));

        assertThatThrownBy(() -> eventos.obtener(null, UUID.randomUUID()))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> assertThat(ex.getStatus()).isEqualTo(503));
    }
}

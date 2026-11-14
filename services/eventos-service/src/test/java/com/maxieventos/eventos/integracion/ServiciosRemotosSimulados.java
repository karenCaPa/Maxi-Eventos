package com.maxieventos.eventos.integracion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP real (el del JDK) que hace de Clientes, Lugares, Contratos y Finanzas en las pruebas de integración:
 * así se prueban de verdad los {@code RestClient} de eventos-service (timeouts, traducción de 404/5xx y reenvío del
 * header {@code Authorization}). Cada ruta responde lo que la prueba programe; una ruta sin programar responde 404.
 */
final class ServiciosRemotosSimulados {

    record Respuesta(int status, String cuerpo) {
    }

    private final HttpServer servidor;
    private final Map<String, Respuesta> respuestas = new ConcurrentHashMap<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();

    private ServiciosRemotosSimulados(HttpServer servidor) {
        this.servidor = servidor;
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    static ServiciosRemotosSimulados iniciar() {
        try {
            return new ServiciosRemotosSimulados(HttpServer.create(new InetSocketAddress("localhost", 0), 0));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    void responder(String ruta, int status, String cuerpo) {
        respuestas.put(ruta, new Respuesta(status, cuerpo));
    }

    /** Valores del header Authorization que llegaron, en orden. */
    List<String> autorizacionesRecibidas() {
        return List.copyOf(autorizaciones);
    }

    void reiniciar() {
        respuestas.clear();
        autorizaciones.clear();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        autorizaciones.add(autorizacion == null ? "(sin token)" : autorizacion);
        Respuesta respuesta = respuestas.getOrDefault(intercambio.getRequestURI().getPath(),
                new Respuesta(404, "{\"status\":404,\"message\":\"No existe\"}"));
        byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(respuesta.status(), cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }
}

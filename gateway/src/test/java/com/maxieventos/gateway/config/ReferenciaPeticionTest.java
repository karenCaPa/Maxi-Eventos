package com.maxieventos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.gateway.security.TokensDePrueba;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fase 11C, de punta a punta por HTTP: el código de referencia ({@code X-Request-Id}) que envía la interfaz llega a TODAS
 * las llamadas REST que hace el gateway por esa petición (también las del DataLoader de proveedores, que se resuelven
 * aparte), vuelve en la respuesta y queda en el log junto al error técnico. Los servicios son un servidor HTTP real.
 */
@SpringBootTest(properties = {TokensDePrueba.PROP_SECRETO, TokensDePrueba.PROP_CORS})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ReferenciaPeticionTest {

    private static final UUID EVENTO = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");
    private static final UUID CLIENTE = UUID.fromString("e146c96a-919d-4e82-b1c9-ebda1bca8b92");
    private static final UUID PROVEEDOR_1 = UUID.fromString("0d137724-90c4-40dd-832b-5fc30b37409f");
    private static final UUID PROVEEDOR_2 = UUID.fromString("1e248835-a1d5-41ee-943c-6fd41c48510a");

    /** "MÉTODO ruta X-Request-Id" de cada petición que recibieron los "servicios". */
    private static final Queue<String> RECIBIDAS = new ConcurrentLinkedQueue<>();
    private static final HttpServer SERVICIOS = iniciar();

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registro) {
        String url = "http://127.0.0.1:" + SERVICIOS.getAddress().getPort();
        for (String s : List.of("auth", "eventos", "clientes", "lugares", "finanzas", "contratos", "proveedores", "auditoria",
                "logistica", "notificaciones")) {
            registro.add("servicios." + s + ".url", () -> url);
        }
    }

    @AfterAll
    static void detener() {
        SERVICIOS.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void limpiar() {
        RECIBIDAS.clear();
    }

    private MvcResult ficha(String referencia) throws Exception {
        String query = "{ evento(id: \\\"" + EVENTO + "\\\") { nombreEvento cliente { nombreCompleto } contrato { estado }"
                + " proveedores { contenido { servicio categoria proveedor { nombre } } } } }";
        var peticion = post("/graphql").contentType(MediaType.APPLICATION_JSON).header("Authorization", TokensDePrueba.valido())
                .content("{\"query\":\"" + query + "\"}");
        if (referencia != null) {
            peticion.header(ReferenciaPeticionFilter.CABECERA, referencia);
        }
        MvcResult inicio = mvc.perform(peticion).andReturn();
        return inicio.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(inicio)).andReturn() : inicio;
    }

    @Test
    void laReferenciaLlegaATodasLasLlamadasVuelveEnLaRespuestaYQuedaEnElLogDelErrorTecnico(CapturedOutput salida)
            throws Exception {
        String referencia = "3f9a12bc-1111-4222-8333-444455556666";

        MvcResult resultado = ficha(referencia);

        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("Quince años de Lucía").contains("Lucía Fernández Ospina").contains("Sonido y Luces")
                .contains("SERVICE_UNAVAILABLE");
        // eventos, clientes, contratos (503), asignaciones y los 2 proveedores del DataLoader
        assertThat(RECIBIDAS).hasSize(6);
        assertThat(RECIBIDAS).allSatisfy(r -> assertThat(r).endsWith(" " + referencia));
        assertThat(RECIBIDAS).anyMatch(r -> r.startsWith("GET /api/v1/proveedores/" + PROVEEDOR_2));
        assertThat(resultado.getResponse().getHeader(ReferenciaPeticionFilter.CABECERA)).isEqualTo(referencia);
        // el detalle del 503 está en el log con la referencia (la interfaz solo muestra "Ref: 3F9A12BC")
        assertThat(salida.getOut()).contains("Error técnico ref=" + referencia + " /evento/contrato -> contratos (503)");
        assertThat(salida.getOut()).contains("[ref=" + referencia + "]");
    }

    @Test
    void unaReferenciaConCaracteresNoPermitidosSeReemplazaPorUnUuidNuevo() throws Exception {
        MvcResult resultado = ficha("x\" inyectada <script> y muy larga para el log");

        String generada = resultado.getResponse().getHeader(ReferenciaPeticionFilter.CABECERA);
        assertThat(generada).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(RECIBIDAS).isNotEmpty().allSatisfy(r -> assertThat(r).endsWith(" " + generada));
    }

    @Test
    void sinReferenciaElGatewayGeneraUna() throws Exception {
        MvcResult resultado = ficha(null);

        String generada = resultado.getResponse().getHeader(ReferenciaPeticionFilter.CABECERA);
        assertThat(UUID.fromString(generada)).isNotNull();
        assertThat(RECIBIDAS).isNotEmpty().allSatisfy(r -> assertThat(r).endsWith(" " + generada));
    }

    @Test
    void normalizarAceptaSoloUnFormatoSeguro() {
        assertThat(ReferenciaPeticionFilter.normalizar("3f9a12bc-1111-4222-8333-444455556666"))
                .isEqualTo("3f9a12bc-1111-4222-8333-444455556666");
        assertThat(ReferenciaPeticionFilter.normalizar("ABCdef12")).isEqualTo("ABCdef12");
        for (String mala : new String[] {null, "", "corta", "con espacio 12345", "linea\nnueva-123", "a".repeat(65)}) {
            assertThat(ReferenciaPeticionFilter.normalizar(mala)).as(String.valueOf(mala))
                    .matches("[0-9a-f]{8}-[0-9a-f-]{27}");
        }
    }

    @Test
    void corsPermiteElHeaderXRequestIdDesdeLaInterfaz() throws Exception {
        mvc.perform(options("/graphql").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization, content-type, x-request-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("x-request-id")));
        mvc.perform(options("/documentos/eventos/" + EVENTO + "/contrato.pdf").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization, accept, x-request-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", Matchers.containsStringIgnoringCase("x-request-id")));
    }

    // ------------------------------------------------------------------ "servicios" simulados

    private static HttpServer iniciar() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", ReferenciaPeticionTest::responder);
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void responder(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        RECIBIDAS.add(intercambio.getRequestMethod() + " " + ruta + " "
                + intercambio.getRequestHeaders().getFirst(ReferenciaPeticionFilter.CABECERA));
        if (ruta.equals("/api/v1/eventos/" + EVENTO)) {
            enviar(intercambio, 200, """
                    {"id":"%s","nombreEvento":"Quince años de Lucía","fecha":"2026-12-05","clienteId":"%s",
                     "tipoEvento":"QUINCE_ANOS","cantidadAdultos":90,"cantidadNinos":15,"numInvitados":105,"zona":"NEIVA",
                     "lugarId":null,"estado":"RESERVADO","fechaCreacion":"2026-11-01T10:00:00Z"}""".formatted(EVENTO, CLIENTE));
        } else if (ruta.equals("/api/v1/clientes/" + CLIENTE)) {
            enviar(intercambio, 200, """
                    {"id":"%s","nombreCompleto":"Lucía Fernández Ospina","telefono":"3101234567","correo":"l@ejemplo.com"}"""
                    .formatted(CLIENTE));
        } else if (ruta.equals("/api/v1/eventos/" + EVENTO + "/contrato")) {
            enviar(intercambio, 503, """
                    {"status":503,"error":"Service Unavailable","message":"El servicio de contratos no está disponible.",
                     "path":"/api/v1/eventos/%s/contrato"}""".formatted(EVENTO));
        } else if (ruta.equals("/api/v1/proveedores/eventos/" + EVENTO)) {
            enviar(intercambio, 200, """
                    {"contenido":[
                      {"id":"%s","eventoId":"%s","proveedorId":"%s","servicioAsignado":"Sonido","costoAcordado":900000,"estado":"CONFIRMADO"},
                      {"id":"%s","eventoId":"%s","proveedorId":"%s","servicioAsignado":"Flores","costoAcordado":300000,"estado":"PENDIENTE"}],
                     "pagina":0,"tamanoPagina":10,"totalElementos":2,"totalPaginas":1}"""
                    .formatted(UUID.randomUUID(), EVENTO, PROVEEDOR_1, UUID.randomUUID(), EVENTO, PROVEEDOR_2));
        } else if (ruta.startsWith("/api/v1/proveedores/")) {
            String id = ruta.substring("/api/v1/proveedores/".length());
            String nombre = id.equals(PROVEEDOR_1.toString()) ? "Sonido y Luces del Huila" : "Floristería Ñapa";
            enviar(intercambio, 200, """
                    {"id":"%s","nombre":"%s","categoria":"SONIDO","contacto":"3110000000","activo":true}""".formatted(id, nombre));
        } else {
            enviar(intercambio, 404, "{\"status\":404,\"message\":\"No simulado: " + ruta + "\"}");
        }
    }

    private static void enviar(HttpExchange intercambio, int codigo, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(codigo, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }
}

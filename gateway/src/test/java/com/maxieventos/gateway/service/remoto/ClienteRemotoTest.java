package com.maxieventos.gateway.service.remoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.PagoInput;
import com.maxieventos.gateway.exception.ErrorCampo;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** La base de los clientes REST: reenvío del token, nombres del esquema y traducción de cada respuesta de error. */
class ClienteRemotoTest {

    private MockRestServiceServer servidor;
    private ClientesClient clientes;
    private FinanzasClient finanzas;
    private final UUID id = UUID.fromString("3c9e0d7a-1b2c-4d5e-8f90-a1b2c3d4e5f6");

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://servicio");
        servidor = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        clientes = new ClientesClient(rest);
        finanzas = new FinanzasClient(rest);
    }

    @Test
    void reenviaElHeaderAuthorizationYLeeElCuerpo() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"nombreCompleto\":\"María José Peña Ñáñez\",\"telefono\":\"3001234567\","
                        + "\"correo\":\"maria@ejemplo.com\",\"direccion\":null,\"campoNuevo\":1}", MediaType.APPLICATION_JSON));

        Cliente cliente = clientes.obtener("Bearer abc", id);

        assertThat(cliente.nombreCompleto()).isEqualTo("María José Peña Ñáñez");
        servidor.verify();
    }

    @Test
    void sinTokenNoSeEnviaAuthorization() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withResourceNotFound());

        assertThat(clientes.obtenerOpcional(null, id)).isNull();
        servidor.verify();
    }

    @Test
    void losNombresDelDtoRestSeLeenConLosNombresDelEsquema() {
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + id + "/pagos?page=0&size=10"))
                .andRespond(withSuccess("{\"contenido\":[{\"pagoId\":\"" + id + "\",\"estado\":\"REGISTRADO\",\"eventIdempotencia\":\"k-1\","
                        + "\"monto\":2610000.00,\"moneda\":\"COP\",\"metodoPago\":\"EFECTIVO\",\"creadoEn\":\"2026-10-28T15:30:00.123Z\"}],"
                        + "\"pagina\":0,\"tamanoPagina\":10,\"totalElementos\":1,\"totalPaginas\":1}", MediaType.APPLICATION_JSON));

        Pagina<Pago> pagos = finanzas.pagos(null, id, 0, 10);

        Pago pago = pagos.contenido().get(0);
        assertThat(pago.id()).isEqualTo(id);
        assertThat(pago.claveIdempotencia()).isEqualTo("k-1");
        assertThat(pago.monto()).isEqualByComparingTo(new BigDecimal("2610000"));
        assertThat(pago.fecha()).hasToString("2026-10-28T15:30:00.123Z");
    }

    @Test
    void error400ConservaMensajeYDetallePorCampo() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"timestamp\":\"2026-10-28T15:30:00Z\",\"status\":400,\"error\":\"Bad Request\","
                        + "\"message\":\"Los datos enviados no son válidos. Revisa los campos indicados.\",\"path\":\"/x\","
                        + "\"errores\":[{\"campo\":\"correo\",\"mensaje\":\"debe ser una dirección de correo electrónico con formato correcto\"}]}"));

        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(400);
            assertThat(ex.getServicio()).isEqualTo("clientes");
            assertThat(ex.getMessage()).isEqualTo("Los datos enviados no son válidos. Revisa los campos indicados.");
            assertThat(ex.getErrores()).containsExactly(
                    new ErrorCampo("correo", "debe ser una dirección de correo electrónico con formato correcto"));
            assertThat(ex.clasificacion().toString()).isEqualTo("BAD_REQUEST");
        });
    }

    @Test
    void error404EnLaQueryRaizEsNotFound() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"status\":404,\"message\":\"No existe el cliente con id " + id + "\"}"));

        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.clasificacion().toString()).isEqualTo("NOT_FOUND");
            assertThat(ex.getMessage()).isEqualTo("No existe el cliente con id " + id);
        });
    }

    @Test
    void error401SinCuerpoTieneUnMensajeEntendible() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.clasificacion().toString()).isEqualTo("UNAUTHORIZED");
            assertThat(ex.getMessage()).startsWith("Falta el token o no es válido. Inicia sesión con la mutation login");
        });
    }

    @Test
    void error409Y503ConSuClasificacion() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"status\":409,\"message\":\"El contrato del evento no está FIRMADO\"}"));
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON).body("{\"status\":503,\"message\":\"El servicio de contratos no está disponible\"}"));

        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.clasificacion().toString()).isEqualTo("CONFLICT");
            assertThat(ex.getMessage()).isEqualTo("El contrato del evento no está FIRMADO");
        });
        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.clasificacion().toString()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(ex.getMessage()).isEqualTo("El servicio de contratos no está disponible");
        });
    }

    @Test
    void registrarPagoEnviaElCuerpoRestYLaClaveEnLaCabecera() {
        servidor.expect(requestTo("http://servicio/api/v1/finanzas/pagos")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "abono-1"))
                .andExpect(content().json("{\"eventoId\":\"" + id + "\",\"clienteId\":\"" + id + "\",\"metodoPago\":\"EFECTIVO\","
                        + "\"monto\":500000,\"moneda\":\"COP\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"pagoId\":\"" + id + "\",\"monto\":500000,\"moneda\":\"COP\"}", MediaType.APPLICATION_JSON));

        Pago pago = finanzas.registrarPago("Bearer abc",
                new PagoInput(id, id, new BigDecimal("500000"), "COP", "EFECTIVO", "abono-1"));

        assertThat(pago.id()).isEqualTo(id);
        servidor.verify();
    }

    @Test
    void loginNoEnviaTokenYUn401ConMensajeConservaElMensajeDeAuth() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth");
        MockRestServiceServer servidorAuth = MockRestServiceServer.bindTo(builder).build();
        AuthClient auth = new AuthClient(builder.build());
        servidorAuth.expect(requestTo("http://auth/api/v1/auth/login")).andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":401,\"message\":\"Correo o contraseña incorrectos.\"}"));

        assertThatThrownBy(() -> auth.login("o@e.com", "mala")).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.clasificacion().toString()).isEqualTo("UNAUTHORIZED");
            assertThat(ex.getMessage()).isEqualTo("Correo o contraseña incorrectos.");
        });
    }

    @Test
    void servicioQueNoRespondeEsServiceUnavailable() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes/" + id)).andRespond(peticion -> {
            throw new IOException("Connection refused");
        });

        assertThatThrownBy(() -> clientes.obtener(null, id)).isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(503);
            assertThat(ex.clasificacion().toString()).isEqualTo("SERVICE_UNAVAILABLE");
            assertThat(ex.getMessage()).isEqualTo("El servicio de clientes no está disponible en este momento. Intenta de nuevo más tarde.");
        });
    }
}

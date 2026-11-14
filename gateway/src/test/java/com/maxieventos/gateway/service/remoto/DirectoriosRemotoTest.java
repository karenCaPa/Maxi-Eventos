package com.maxieventos.gateway.service.remoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maxieventos.gateway.dto.ItemCompraDatosInput;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.LugarInput;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorInput;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Fase 10B3: lo que el gateway envía a Lugares y Proveedores al crear, actualizar y cambiar el estado (10B3b) y a Logística
 * al agregar, editar y quitar ítems de la lista de compras (10B3c).
 */
class DirectoriosRemotoTest {

    private MockRestServiceServer servidor;
    private LugaresClient lugares;
    private ProveedoresClient proveedores;
    private RestClient restCompartido;
    private final UUID id = UUID.fromString("5b0c9f3e-7a2d-4c1b-9e8f-0a1b2c3d4e5f");

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://servicio");
        servidor = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        restCompartido = rest;
        lugares = new LugaresClient(rest);
        proveedores = new ProveedoresClient(rest);
    }

    private static String lugarJson(String estado) {
        return "{\"id\":\"5b0c9f3e-7a2d-4c1b-9e8f-0a1b2c3d4e5f\",\"nombre\":\"Hacienda Las Ceibas\",\"direccion\":\"Km 5\","
                + "\"tipo\":\"HACIENDA\",\"capacidad\":250,\"estado\":\"" + estado + "\"}";
    }

    @Test
    void crearLugarHacePostConElCuerpoYElToken() {
        servidor.expect(requestTo("http://servicio/api/v1/lugares"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer abc"))
                .andExpect(content().json("{\"nombre\":\"Hacienda Las Ceibas\",\"direccion\":\"Km 5\",\"tipo\":\"HACIENDA\",\"capacidad\":250}",
                        JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(lugarJson("ACTIVO")));

        Lugar lugar = lugares.crear("Bearer abc", new LugarInput("Hacienda Las Ceibas", "Km 5", "HACIENDA", 250));

        assertThat(lugar.id()).isEqualTo(id);
        assertThat(lugar.estado()).isEqualTo("ACTIVO");
        servidor.verify();
    }

    @Test
    void actualizarYCambiarEstadoDelLugarUsanPutYPatch() {
        servidor.expect(requestTo("http://servicio/api/v1/lugares/" + id))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"nombre\":\"Hacienda Las Ceibas\",\"direccion\":\"Km 5\",\"tipo\":\"HACIENDA\",\"capacidad\":250}"))
                .andRespond(withSuccess(lugarJson("ACTIVO"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/lugares/" + id + "/estado"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"estado\":\"INACTIVO\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(lugarJson("INACTIVO"), MediaType.APPLICATION_JSON));

        lugares.actualizar("Bearer abc", id, new LugarInput("Hacienda Las Ceibas", "Km 5", "HACIENDA", 250));
        assertThat(lugares.cambiarEstado("Bearer abc", id, "INACTIVO").estado()).isEqualTo("INACTIVO");
        servidor.verify();
    }

    @Test
    void proveedoresUsanPostPutYPatchConActivo() {
        String json = "{\"id\":\"" + id + "\",\"nombre\":\"Flores del Huila\",\"categoria\":\"FLORISTERIA\",\"contacto\":\"310\",\"activo\":%s}";
        servidor.expect(requestTo("http://servicio/api/v1/proveedores"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"nombre\":\"Flores del Huila\",\"categoria\":\"FLORISTERIA\",\"contacto\":\"310\"}", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(json.formatted("true")));
        servidor.expect(requestTo("http://servicio/api/v1/proveedores/" + id))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(json.formatted("true"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/proveedores/" + id + "/estado"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"activo\":false}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(json.formatted("false"), MediaType.APPLICATION_JSON));

        ProveedorInput input = new ProveedorInput("Flores del Huila", "FLORISTERIA", "310");
        assertThat(proveedores.crear("Bearer abc", input).activo()).isTrue();
        proveedores.actualizar("Bearer abc", id, input);
        Proveedor inactivo = proveedores.cambiarEstado("Bearer abc", id, false);
        assertThat(inactivo.activo()).isFalse();
        servidor.verify();
    }

    // ------------------------------------------------------------------ 10B3c: ítems de la lista de compras (Logística)

    @Test
    void itemsDeCompraUsanPostPutYDeleteConElCuerpoYElToken() {
        LogisticaClient logistica = new LogisticaClient(restCompartido);
        UUID eventoId = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");
        String json = "{\"id\":\"" + id + "\",\"nombreItem\":\"Recuerdos\",\"cantidadEstimada\":\"100\",\"comprado\":false,"
                + "\"origen\":\"MANUAL\",\"nota\":null}";
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/compras"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer abc"))
                .andExpect(content().json("{\"nombreItem\":\"Recuerdos\",\"cantidadEstimada\":\"100\",\"nota\":null}", JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(json));
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/compras/" + id))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"nombreItem\":\"Recuerdos\",\"cantidadEstimada\":\"120\",\"nota\":\"Azules\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/compras/" + id))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(logistica.agregarItem("Bearer abc", eventoId, new ItemCompraDatosInput("Recuerdos", "100", null)).origen())
                .isEqualTo("MANUAL");
        logistica.editarItem("Bearer abc", eventoId, id, new ItemCompraDatosInput("Recuerdos", "120", "Azules"));
        logistica.quitarItem("Bearer abc", eventoId, id);
        servidor.verify();
    }

    @Test
    void quitarUnCalculadoConservaEl409DeLogistica() {
        LogisticaClient logistica = new LogisticaClient(restCompartido);
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + id + "/compras/" + id))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"error\":\"Conflict\",\"message\":\"Los ítems calculados por el sistema no se pueden quitar.\"}"));

        assertThatThrownBy(() -> logistica.quitarItem("Bearer abc", id, id))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getServicio()).isEqualTo("logistica");
                });
    }

    @Test
    void elDuplicadoConservaEl409YElMensajeDeProveedores() {
        servidor.expect(requestTo("http://servicio/api/v1/proveedores"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"error\":\"Conflict\",\"message\":\"Ya existe un proveedor con el nombre Flores del Huila\"}"));

        assertThatThrownBy(() -> proveedores.crear("Bearer abc", new ProveedorInput("flores del huila", "FLORISTERIA", "310")))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getServicio()).isEqualTo("proveedores");
                    assertThat(ex.getMessage()).isEqualTo("Ya existe un proveedor con el nombre Flores del Huila");
                });
    }
}

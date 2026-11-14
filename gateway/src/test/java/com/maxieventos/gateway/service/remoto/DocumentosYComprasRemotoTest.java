package com.maxieventos.gateway.service.remoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.Documento;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.ItemCompraInput;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Fase 10: lo que el gateway envía a Contratos (PDF), Logística (compras) y Clientes (directorio). */
class DocumentosYComprasRemotoTest {

    private MockRestServiceServer servidor;
    private ContratosClient contratos;
    private LogisticaClient logistica;
    private ClientesClient clientes;
    private final UUID eventoId = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");
    private final UUID item = UUID.fromString("3c9e0d7a-1b2c-4d5e-8f90-a1b2c3d4e5f6");

    @BeforeEach
    void crear() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://servicio");
        servidor = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        contratos = new ContratosClient(rest);
        logistica = new LogisticaClient(rest);
        clientes = new ClientesClient(rest);
    }

    @Test
    void elPdfDelContratoReenviaElTokenAceptaPdfYJsonYConservaLosBytes() {
        byte[] pdf = "%PDF-1.7 contrato".getBytes(StandardCharsets.US_ASCII);
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/contrato/pdf"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer abc"))
                .andExpect(header("Accept", "application/pdf, application/json"))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        Documento documento = contratos.pdfContrato("Bearer abc", eventoId);

        assertThat(documento.contenido()).isEqualTo(pdf);
        assertThat(documento.nombreArchivo()).isEqualTo("contrato-" + eventoId + ".pdf");
        servidor.verify();
    }

    @Test
    void elAnexoSinAdicionalesConservaEl409YElMensajeDeContratos() {
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/adicionales/anexo-pdf"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"error\":\"Conflict\",\"message\":\"El evento no tiene servicios adicionales.\"}"));

        assertThatThrownBy(() -> contratos.pdfAnexo("Bearer abc", eventoId))
                .isInstanceOfSatisfying(ServicioRemotoException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(409);
                    assertThat(ex.getServicio()).isEqualTo("contratos");
                    assertThat(ex.getMessage()).isEqualTo("El evento no tiene servicios adicionales.");
                });
    }

    @Test
    void actualizarComprasHaceUnPutConLaListaDeItems() {
        servidor.expect(requestTo("http://servicio/api/v1/eventos/" + eventoId + "/compras?page=0&size=100"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer abc"))
                .andExpect(content().json("[{\"id\":\"" + item + "\",\"comprado\":true}]", JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"contenido\":[{\"id\":\"" + item + "\",\"nombreItem\":\"Bebidas\",\"cantidadEstimada\":\"210 unidades\","
                        + "\"comprado\":true}],\"pagina\":0,\"tamanoPagina\":100,\"totalElementos\":1,\"totalPaginas\":1}", MediaType.APPLICATION_JSON));

        Pagina<ItemCompra> pagina = logistica.actualizarCompras("Bearer abc", eventoId, List.of(new ItemCompraInput(item, true)), 0, 100);

        assertThat(pagina.contenido()).containsExactly(new ItemCompra(item, "Bebidas", "210 unidades", true, null, null));
        servidor.verify();
    }

    @Test
    void elDirectorioDeClientesEnviaLosFiltrosQueVienen() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes?nombre=Luc%C3%ADa&page=0&size=20&sort=nombreCompleto,asc"))
                .andRespond(withSuccess("{\"contenido\":[{\"id\":\"" + item + "\",\"nombreCompleto\":\"Lucía Fernández Ospina\","
                        + "\"telefono\":\"3101234567\",\"correo\":\"l@ejemplo.com\"}],\"pagina\":0,\"tamanoPagina\":20,"
                        + "\"totalElementos\":1,\"totalPaginas\":1}", MediaType.APPLICATION_JSON));

        Pagina<Cliente> pagina = clientes.listar("Bearer abc", "Lucía", 0, 20, "nombreCompleto,asc");

        assertThat(pagina.contenido().get(0).nombreCompleto()).isEqualTo("Lucía Fernández Ospina");
        servidor.verify();
    }

    @Test
    void elDirectorioDeClientesSinFiltrosNoEnviaNombreNiSort() {
        servidor.expect(requestTo("http://servicio/api/v1/clientes?page=0&size=10"))
                .andRespond(withSuccess("{\"contenido\":[],\"pagina\":0,\"tamanoPagina\":10,\"totalElementos\":0,\"totalPaginas\":0}",
                        MediaType.APPLICATION_JSON));

        assertThat(clientes.listar("Bearer abc", null, 0, 10, null).totalElementos()).isZero();
        servidor.verify();
    }
}

package com.maxieventos.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.gateway.config.CargadoresConfig;
import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.ItemCompraInput;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.exception.ErrorRemoto;
import com.maxieventos.gateway.exception.ErroresGraphQl;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import com.maxieventos.gateway.service.remoto.AuditoriaClient;
import com.maxieventos.gateway.service.remoto.AuthClient;
import com.maxieventos.gateway.service.remoto.ClientesClient;
import com.maxieventos.gateway.service.remoto.ContratosClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.FinanzasClient;
import com.maxieventos.gateway.service.remoto.LogisticaClient;
import com.maxieventos.gateway.service.remoto.LugaresClient;
import com.maxieventos.gateway.service.remoto.NotificacionesClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Fase 10: query clientes (directorio para crear eventos) y mutation actualizarCompras; ambas delegan en una llamada REST. */
@GraphQlTest({ConsultasController.class, MutacionesController.class, EventoCamposController.class, CamposAnidadosController.class})
@Import({GraphQlConfig.class, CargadoresConfig.class, ErroresGraphQl.class})
class ClientesYComprasGraphQlTest {

    @Autowired
    private GraphQlTester tester;

    @MockitoBean private AuthClient auth;
    @MockitoBean private EventosClient eventos;
    @MockitoBean private ClientesClient clientes;
    @MockitoBean private LugaresClient lugares;
    @MockitoBean private FinanzasClient finanzas;
    @MockitoBean private ContratosClient contratos;
    @MockitoBean private ProveedoresClient proveedores;
    @MockitoBean private LogisticaClient logistica;
    @MockitoBean private NotificacionesClient notificaciones;
    @MockitoBean private AuditoriaClient auditoria;

    private final UUID eventoId = UUID.randomUUID();
    private final UUID bebidas = UUID.randomUUID();
    private final UUID hielo = UUID.randomUUID();

    @Test
    void clientesDelegaElFiltroYLaPaginacionYDevuelveLaPagina() {
        UUID id = UUID.randomUUID();
        when(clientes.listar(any(), eq("Lucía"), eq(0), eq(20), eq("nombreCompleto,asc")))
                .thenReturn(new Pagina<>(List.of(new Cliente(id, "Lucía Fernández Ospina", "3101234567", "lucia@ejemplo.com", null)),
                        0, 20, 1, 1));

        tester.document("{ clientes(nombre: \"Lucía\", size: 20, sort: \"nombreCompleto,asc\") "
                        + "{ totalElementos contenido { id nombreCompleto correo } } }")
                .execute()
                .path("clientes.totalElementos").entity(Long.class).isEqualTo(1L)
                .path("clientes.contenido[0].nombreCompleto").entity(String.class).isEqualTo("Lucía Fernández Ospina")
                .path("clientes.contenido[0].id").entity(String.class).isEqualTo(id.toString());
    }

    @Test
    void clientesSinArgumentosUsaLosValoresPorDefectoDelSchema() {
        when(clientes.listar(any(), any(), anyInt(), anyInt(), any())).thenReturn(new Pagina<>(List.of(), 0, 10, 0, 0));

        tester.document("{ clientes { totalElementos } }").execute().path("clientes.totalElementos").entity(Long.class).isEqualTo(0L);

        verify(clientes).listar(any(), eq(null), eq(0), eq(10), eq(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualizarComprasEnviaLosItemsTalCualYDevuelveLaListaActualizada() {
        when(logistica.actualizarCompras(any(), eq(eventoId), any(), eq(0), eq(100))).thenReturn(new Pagina<>(List.of(
                new ItemCompra(bebidas, "Bebidas", "210 unidades", true, "CALCULADO", null),
                new ItemCompra(hielo, "Hielo", "11 bolsas", false, "CALCULADO", null)), 0, 100, 2, 1));

        tester.document("mutation($e: ID!, $i: [ItemCompraInput!]!) { actualizarCompras(eventoId: $e, items: $i) "
                        + "{ totalElementos contenido { id nombreItem comprado } } }")
                .variable("e", eventoId.toString())
                .variable("i", List.of(Map.of("id", bebidas.toString(), "comprado", true), Map.of("id", hielo.toString(), "comprado", false)))
                .execute()
                .path("actualizarCompras.totalElementos").entity(Long.class).isEqualTo(2L)
                .path("actualizarCompras.contenido[0].comprado").entity(Boolean.class).isEqualTo(true)
                .path("actualizarCompras.contenido[1].nombreItem").entity(String.class).isEqualTo("Hielo");

        ArgumentCaptor<List<ItemCompraInput>> items = ArgumentCaptor.forClass(List.class);
        verify(logistica).actualizarCompras(any(), eq(eventoId), items.capture(), eq(0), eq(100));
        assertThat(items.getValue()).containsExactly(new ItemCompraInput(bebidas, true), new ItemCompraInput(hielo, false));
    }

    @Test
    void unItemDeOtroEventoEsNotFoundConElMensajeDeLogistica() {
        String mensaje = "No existe el ítem de compra " + hielo + " para el evento " + eventoId;
        when(logistica.actualizarCompras(any(), eq(eventoId), any(), anyInt(), anyInt()))
                .thenThrow(ServicioRemotoException.deRespuesta("logistica", 404, new ErrorRemoto(404, "Not Found", mensaje, null, List.of())));

        tester.document("mutation($e: ID!) { actualizarCompras(eventoId: $e, items: [{id: \"" + hielo + "\", comprado: true}]) "
                        + "{ totalElementos } }")
                .variable("e", eventoId.toString())
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getMessage()).isEqualTo(mensaje);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                    assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "logistica").containsEntry("status", 404);
                    assertThat(errores.get(0).getPath()).isEqualTo("actualizarCompras");
                });
    }
}

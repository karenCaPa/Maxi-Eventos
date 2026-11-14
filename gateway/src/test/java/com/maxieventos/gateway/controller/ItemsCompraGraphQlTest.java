package com.maxieventos.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.gateway.config.CargadoresConfig;
import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.ItemCompraDatosInput;
import com.maxieventos.gateway.exception.ErrorCampo;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Fase 10B3: agregar, editar y quitar ítems de la lista de compras; cada mutation delega en una llamada a Logística. */
@GraphQlTest({ConsultasController.class, MutacionesController.class, EventoCamposController.class, CamposAnidadosController.class})
@Import({GraphQlConfig.class, CargadoresConfig.class, ErroresGraphQl.class})
class ItemsCompraGraphQlTest {

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
    private final UUID itemId = UUID.randomUUID();

    private static ErrorRemoto error(int status, String mensaje, List<ErrorCampo> errores) {
        return new ErrorRemoto(status, "x", mensaje, null, errores);
    }

    @Test
    void agregarEnviaElInputTalCualYDevuelveElItemManual() {
        ItemCompraDatosInput input = new ItemCompraDatosInput("Recuerdos para invitados", "100 unidades", "Bolsas con dulces");
        when(logistica.agregarItem(any(), eq(eventoId), eq(input)))
                .thenReturn(new ItemCompra(itemId, "Recuerdos para invitados", "100 unidades", false, "MANUAL", "Bolsas con dulces"));

        tester.document("mutation($e: ID!, $i: ItemCompraDatosInput!) { agregarItemCompra(eventoId: $e, input: $i) "
                        + "{ id nombreItem comprado origen nota } }")
                .variable("e", eventoId.toString())
                .variable("i", Map.of("nombreItem", "Recuerdos para invitados", "cantidadEstimada", "100 unidades", "nota", "Bolsas con dulces"))
                .execute()
                .path("agregarItemCompra.id").entity(String.class).isEqualTo(itemId.toString())
                .path("agregarItemCompra.origen").entity(String.class).isEqualTo("MANUAL")
                .path("agregarItemCompra.comprado").entity(Boolean.class).isEqualTo(false)
                .path("agregarItemCompra.nota").entity(String.class).isEqualTo("Bolsas con dulces");
    }

    @Test
    void editarDelegaYConservaLoQueDevuelveLogistica() {
        ItemCompraDatosInput input = new ItemCompraDatosInput("Hielo en cubos", "15 bolsas", null);
        when(logistica.editarItem(any(), eq(eventoId), eq(itemId), eq(input)))
                .thenReturn(new ItemCompra(itemId, "Hielo en cubos", "15 bolsas", true, "CALCULADO", null));

        tester.document("mutation($e: ID!, $id: ID!) { editarItemCompra(eventoId: $e, itemId: $id, "
                        + "input: {nombreItem: \"Hielo en cubos\", cantidadEstimada: \"15 bolsas\"}) { nombreItem comprado origen } }")
                .variable("e", eventoId.toString()).variable("id", itemId.toString())
                .execute()
                .path("editarItemCompra.comprado").entity(Boolean.class).isEqualTo(true)
                .path("editarItemCompra.origen").entity(String.class).isEqualTo("CALCULADO");
    }

    @Test
    void quitarDevuelveElIdQuitado() {
        tester.document("mutation($e: ID!, $id: ID!) { quitarItemCompra(eventoId: $e, itemId: $id) }")
                .variable("e", eventoId.toString()).variable("id", itemId.toString())
                .execute()
                .path("quitarItemCompra").entity(String.class).isEqualTo(itemId.toString());

        verify(logistica).quitarItem(any(), eq(eventoId), eq(itemId));
    }

    @Test
    void quitarUnCalculadoEsConflictConElMensajeDeLogistica() {
        String mensaje = "Los ítems calculados por el sistema no se pueden quitar; puedes editarlos o dejarlos sin marcar.";
        doThrow(ServicioRemotoException.deRespuesta("logistica", 409, error(409, mensaje, List.of())))
                .when(logistica).quitarItem(any(), eq(eventoId), eq(itemId));

        tester.document("mutation($e: ID!, $id: ID!) { quitarItemCompra(eventoId: $e, itemId: $id) }")
                .variable("e", eventoId.toString()).variable("id", itemId.toString())
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getMessage()).isEqualTo(mensaje);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("CONFLICT");
                    assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "logistica").containsEntry("status", 409);
                });
    }

    @Test
    void agregarConDatosInvalidosEsBadRequestConErroresPorCampo() {
        when(logistica.agregarItem(any(), eq(eventoId), any())).thenThrow(ServicioRemotoException.deRespuesta("logistica", 400,
                error(400, "Los datos enviados no son válidos. Revisa los campos indicados.",
                        List.of(new ErrorCampo("nombreItem", "no debe estar vacío")))));

        tester.document("mutation($e: ID!) { agregarItemCompra(eventoId: $e, input: {nombreItem: \"\", cantidadEstimada: \"2\"}) { id } }")
                .variable("e", eventoId.toString())
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                    assertThat(errores.get(0).getExtensions().get("errores").toString()).contains("nombreItem");
                });
    }

    @Test
    void agregarAUnEventoSinLogisticaEsNotFound() {
        when(logistica.agregarItem(any(), eq(eventoId), any())).thenThrow(ServicioRemotoException.deRespuesta("logistica", 404,
                error(404, "No existe logística para el evento " + eventoId, List.of())));

        tester.document("mutation($e: ID!) { agregarItemCompra(eventoId: $e, input: {nombreItem: \"Velas\", cantidadEstimada: \"20\"}) { id } }")
                .variable("e", eventoId.toString())
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                    assertThat(errores.get(0).getMessage()).isEqualTo("No existe logística para el evento " + eventoId);
                });
    }
}

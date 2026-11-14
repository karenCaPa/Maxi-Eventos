package com.maxieventos.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maxieventos.gateway.config.CargadoresConfig;
import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.LugarInput;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorInput;
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

/** Fase 10B3: mutations de los directorios de lugares y proveedores; cada una delega en una llamada REST. */
@GraphQlTest({ConsultasController.class, MutacionesController.class, DirectoriosController.class, EventoCamposController.class,
        CamposAnidadosController.class})
@Import({GraphQlConfig.class, CargadoresConfig.class, ErroresGraphQl.class})
class DirectoriosGraphQlTest {

    /** Fase 11D: el slice @GraphQlTest no pasa por TokenInterceptor; el rol del token (ADMINISTRADOR) se pone en el contexto. */
    private GraphQlTester tester;

    @Autowired
    void comoAdministrador(org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester base) {
        this.tester = base.mutate().configureExecutionInput((entrada, builder) -> builder.graphQLContext(
                c -> c.put(com.maxieventos.gateway.config.TokenInterceptor.ROL, "ADMINISTRADOR")).build()).build();
    }

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

    private final UUID id = UUID.randomUUID();

    @Test
    void crearLugarEnviaElInputTalCualYDevuelveElLugar() {
        LugarInput input = new LugarInput("Hacienda Las Ceibas", "Km 5 vía Rivera", "HACIENDA", 250);
        when(lugares.crear(any(), eq(input))).thenReturn(new Lugar(id, "Hacienda Las Ceibas", "Km 5 vía Rivera", "HACIENDA", 250, "ACTIVO"));

        tester.document("mutation($i: LugarInput!) { crearLugar(input: $i) { id nombre tipo capacidad estado } }")
                .variable("i", Map.of("nombre", "Hacienda Las Ceibas", "direccion", "Km 5 vía Rivera", "tipo", "HACIENDA", "capacidad", 250))
                .execute()
                .path("crearLugar.id").entity(String.class).isEqualTo(id.toString())
                .path("crearLugar.estado").entity(String.class).isEqualTo("ACTIVO")
                .path("crearLugar.capacidad").entity(Integer.class).isEqualTo(250);
    }

    @Test
    void crearLugarSinCapacidadLlegaAlServicioYSuErrorPorCampoSeConserva() {
        when(lugares.crear(any(), eq(new LugarInput("Salón", "Cra. 5", "SALON", null))))
                .thenThrow(ServicioRemotoException.deRespuesta("lugares", 400, new ErrorRemoto(400, "Bad Request",
                        "Los datos enviados no son válidos. Revisa los campos indicados.", null,
                        List.of(new ErrorCampo("capacidad", "no debe ser nulo")))));

        tester.document("mutation { crearLugar(input: {nombre: \"Salón\", direccion: \"Cra. 5\", tipo: \"SALON\"}) { id } }")
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                    assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "lugares").containsEntry("status", 400);
                    assertThat(errores.get(0).getExtensions().get("errores").toString()).contains("capacidad").contains("no debe ser nulo");
                });
    }

    @Test
    void actualizarYCambiarEstadoDelLugarDelegan() {
        LugarInput input = new LugarInput("Salón Los Ocobos", "Calle 7 # 4-10", "SALON", 180);
        when(lugares.actualizar(any(), eq(id), eq(input))).thenReturn(new Lugar(id, "Salón Los Ocobos", "Calle 7 # 4-10", "SALON", 180, "ACTIVO"));
        when(lugares.cambiarEstado(any(), eq(id), eq("INACTIVO"))).thenReturn(new Lugar(id, "Salón Los Ocobos", "Calle 7 # 4-10", "SALON", 180, "INACTIVO"));

        tester.document("mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { capacidad } }")
                .variable("id", id.toString())
                .variable("i", Map.of("nombre", "Salón Los Ocobos", "direccion", "Calle 7 # 4-10", "tipo", "SALON", "capacidad", 180))
                .execute()
                .path("actualizarLugar.capacidad").entity(Integer.class).isEqualTo(180);
        tester.document("mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: INACTIVO) { estado } }")
                .variable("id", id.toString())
                .execute()
                .path("cambiarEstadoLugar.estado").entity(String.class).isEqualTo("INACTIVO");

        verify(lugares).cambiarEstado(any(), eq(id), eq("INACTIVO"));
    }

    @Test
    void crearActualizarYDesactivarProveedorDelegan() {
        ProveedorInput input = new ProveedorInput("Flores del Huila", "FLORISTERIA", "3104567890");
        when(proveedores.crear(any(), eq(input))).thenReturn(new Proveedor(id, "Flores del Huila", "FLORISTERIA", "3104567890", true));
        when(proveedores.actualizar(any(), eq(id), eq(input))).thenReturn(new Proveedor(id, "Flores del Huila", "FLORISTERIA", "3104567890", true));
        when(proveedores.cambiarEstado(any(), eq(id), eq(false))).thenReturn(new Proveedor(id, "Flores del Huila", "FLORISTERIA", "3104567890", false));
        Map<String, Object> variables = Map.of("nombre", "Flores del Huila", "categoria", "FLORISTERIA", "contacto", "3104567890");

        tester.document("mutation($i: ProveedorInput!) { crearProveedor(input: $i) { id activo } }").variable("i", variables)
                .execute()
                .path("crearProveedor.id").entity(String.class).isEqualTo(id.toString())
                .path("crearProveedor.activo").entity(Boolean.class).isEqualTo(true);
        tester.document("mutation($id: ID!, $i: ProveedorInput!) { actualizarProveedor(id: $id, input: $i) { nombre } }")
                .variable("id", id.toString()).variable("i", variables)
                .execute()
                .path("actualizarProveedor.nombre").entity(String.class).isEqualTo("Flores del Huila");
        tester.document("mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { activo } }")
                .variable("id", id.toString())
                .execute()
                .path("cambiarEstadoProveedor.activo").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void proveedorDuplicadoEsConflictConElMensajeDeProveedores() {
        String mensaje = "Ya existe un proveedor con el nombre Sonido y Luces del Huila";
        when(proveedores.crear(any(), any())).thenThrow(ServicioRemotoException.deRespuesta("proveedores", 409,
                new ErrorRemoto(409, "Conflict", mensaje, null, List.of())));

        tester.document("mutation { crearProveedor(input: {nombre: \"sonido y luces del huila\", categoria: \"SONIDO\", contacto: \"3001112233\"}) { id } }")
                .execute()
                .errors()
                .satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getMessage()).isEqualTo(mensaje);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("CONFLICT");
                    assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "proveedores").containsEntry("status", 409);
                    assertThat(errores.get(0).getPath()).isEqualTo("crearProveedor");
                });
    }

    @Test
    void lugarInexistenteEsNotFound() {
        when(lugares.cambiarEstado(any(), eq(id), eq("ACTIVO"))).thenThrow(ServicioRemotoException.deRespuesta("lugares", 404,
                new ErrorRemoto(404, "Not Found", "No existe un lugar con id " + id, null, List.of())));

        tester.document("mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: ACTIVO) { estado } }")
                .variable("id", id.toString())
                .execute()
                .errors()
                .satisfy(errores -> assertThat(errores.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND"));
    }
}

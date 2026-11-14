package com.maxieventos.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.gateway.config.CargadoresConfig;
import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.dto.AsignarProveedorInput;
import com.maxieventos.gateway.dto.AuthPayload;
import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.CrearEventoInput;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.PagoInput;
import com.maxieventos.gateway.dto.ProveedorAsignado;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

/** Las mutations delegan en REST sin lógica propia; los errores del servicio llegan como errores GraphQL legibles. */
@GraphQlTest({MutacionesController.class, EventoCamposController.class, CamposAnidadosController.class})
@Import({GraphQlConfig.class, CargadoresConfig.class, ErroresGraphQl.class})
class MutacionesGraphQlTest {

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
    private final UUID clienteId = UUID.randomUUID();

    private Evento evento(String estado) {
        return new Evento(eventoId, "Boda de Ana y Luis", LocalDate.of(2027, 1, 16), clienteId, "BODA", 100, 10, 110, "RURAL",
                null, estado, OffsetDateTime.parse("2026-10-28T15:30:00Z"));
    }

    @Test
    void crearEventoDelegaElInputTalCualYPermitePedirLasRelacionesDelResultado() {
        when(eventos.crear(any(), any())).thenReturn(evento("PENDIENTE"));
        when(clientes.obtenerOpcional(any(), eq(clienteId)))
                .thenReturn(new Cliente(clienteId, "Ana Gómez Ñáñez", "3001234567", "ana@ejemplo.com", null));

        tester.document("""
                mutation($input: CrearEventoInput!) { crearEvento(input: $input) { id estado cliente { nombreCompleto } } }""")
                .variable("input", Map.of("nombreEvento", "Boda de Ana y Luis", "clienteId", clienteId.toString(),
                        "fecha", "2027-01-16", "tipoEvento", "BODA", "cantidadAdultos", 100, "cantidadNinos", 10, "zona", "RURAL"))
                .execute()
                .path("crearEvento.estado").entity(String.class).isEqualTo("PENDIENTE")
                .path("crearEvento.cliente.nombreCompleto").entity(String.class).isEqualTo("Ana Gómez Ñáñez");

        ArgumentCaptor<CrearEventoInput> enviado = ArgumentCaptor.forClass(CrearEventoInput.class);
        verify(eventos).crear(any(), enviado.capture());
        assertThat(enviado.getValue()).isEqualTo(new CrearEventoInput("Boda de Ana y Luis", clienteId, LocalDate.of(2027, 1, 16),
                "BODA", 100, 10, "RURAL", null));
    }

    @Test
    void registrarPagoEnviaLaClaveDeIdempotenciaYLaMonedaPorDefecto() {
        UUID pagoId = UUID.randomUUID();
        when(finanzas.registrarPago(any(), any())).thenReturn(new Pago(pagoId, "REGISTRADO", "abono-1", eventoId, clienteId,
                new BigDecimal("3966000"), "COP", "TRANSFERENCIA", OffsetDateTime.parse("2026-10-28T15:30:00Z")));

        tester.document("""
                mutation($input: PagoInput!) { registrarPago(input: $input) { id monto moneda claveIdempotencia } }""")
                .variable("input", Map.of("eventoId", eventoId.toString(), "clienteId", clienteId.toString(), "monto", "3966000",
                        "metodoPago", "TRANSFERENCIA", "claveIdempotencia", "abono-1"))
                .execute()
                .path("registrarPago.id").entity(String.class).isEqualTo(pagoId.toString())
                .path("registrarPago.monto").entity(BigDecimal.class).isEqualTo(new BigDecimal("3966000"));

        ArgumentCaptor<PagoInput> enviado = ArgumentCaptor.forClass(PagoInput.class);
        verify(finanzas).registrarPago(any(), enviado.capture());
        assertThat(enviado.getValue().claveIdempotencia()).isEqualTo("abono-1");
        assertThat(enviado.getValue().moneda()).isEqualTo("COP"); // valor por defecto del schema
        assertThat(enviado.getValue().monto()).isEqualByComparingTo("3966000");
    }

    @Test
    void confirmarSinContratoFirmadoEsConflictConElMensajeDelServicio() {
        String mensaje = "No se puede confirmar el evento: el contrato debe estar FIRMADO (estado actual: PENDIENTE).";
        when(eventos.confirmar(any(), eq(eventoId))).thenThrow(ServicioRemotoException.deRespuesta("eventos", 409,
                new ErrorRemoto(409, "Conflict", mensaje, "/api/v1/eventos/x/confirmar", null)));

        tester.document("mutation($id: ID!) { confirmarEvento(id: $id) { estado } }")
                .variable("id", eventoId.toString())
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("CONFLICT");
                    assertThat(errores.get(0).getMessage()).isEqualTo(mensaje);
                    assertThat(errores.get(0).getPath()).isEqualTo("confirmarEvento");
                    assertThat(errores.get(0).getExtensions()).containsEntry("status", 409).containsEntry("servicio", "eventos");
                })
                // Evento! no admite null: el error se propaga y data queda null (comportamiento estándar de GraphQL)
                .path("confirmarEvento").pathDoesNotExist();
    }

    @Test
    void crearClienteConDatosInvalidosEsBadRequestConLosErroresPorCampo() {
        when(clientes.crear(any(), any())).thenThrow(ServicioRemotoException.deRespuesta("clientes", 400,
                new ErrorRemoto(400, "Bad Request", "Los datos enviados no son válidos. Revisa los campos indicados.", "/api/v1/clientes",
                        List.of(new ErrorCampo("correo", "debe ser una dirección de correo electrónico con formato correcto")))));

        tester.document("""
                mutation { crearCliente(input: { nombreCompleto: "Ana Gómez", telefono: "3001234567", correo: "ana@@ejemplo" }) { id } }""")
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                    assertThat(errores.get(0).getExtensions().get("errores")).isEqualTo(List.of(
                            Map.of("campo", "correo", "mensaje", "debe ser una dirección de correo electrónico con formato correcto")));
                });
    }

    @Test
    void loginSinTokenYCredencialesIncorrectasConElMensajeDeAuth() {
        when(auth.login("organizadora@ejemplo.com", "correcta")).thenReturn(new AuthPayload("eyJ...", "Bearer", 28800, "Organizadora", "ADMINISTRADOR"));
        when(auth.login("organizadora@ejemplo.com", "otra")).thenThrow(ServicioRemotoException.deRespuesta("auth", 401,
                new ErrorRemoto(401, "Unauthorized", "Correo o contraseña incorrectos.", "/api/v1/auth/login", null)));

        tester.document("mutation { login(correo: \"organizadora@ejemplo.com\", password: \"correcta\") { tipoToken expiracionEnSegundos nombre rol } }")
                .execute()
                .path("login.tipoToken").entity(String.class).isEqualTo("Bearer")
                .path("login.expiracionEnSegundos").entity(Long.class).isEqualTo(28800L)
                // Fase 11D
                .path("login.nombre").entity(String.class).isEqualTo("Organizadora")
                .path("login.rol").entity(String.class).isEqualTo("ADMINISTRADOR");
        tester.document("mutation { login(correo: \"organizadora@ejemplo.com\", password: \"otra\") { token } }")
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("UNAUTHORIZED");
                    assertThat(errores.get(0).getMessage()).isEqualTo("Correo o contraseña incorrectos.");
                });
    }

    @Test
    void asignarProveedorEnviaElServicioComoServicioAsignado() {
        UUID proveedorId = UUID.randomUUID();
        when(proveedores.asignar(any(), any())).thenReturn(new ProveedorAsignado(UUID.randomUUID(), eventoId, proveedorId,
                "Sonido y luces", new BigDecimal("900000"), "PENDIENTE"));

        tester.document("""
                mutation { asignarProveedor(input: { eventoId: "%s", proveedorId: "%s", servicio: "Sonido y luces", costoAcordado: 900000 })
                { servicio estado } }""".formatted(eventoId, proveedorId))
                .execute()
                .path("asignarProveedor.servicio").entity(String.class).isEqualTo("Sonido y luces");

        ArgumentCaptor<AsignarProveedorInput> enviado = ArgumentCaptor.forClass(AsignarProveedorInput.class);
        verify(proveedores).asignar(any(), enviado.capture());
        assertThat(enviado.getValue().cuerpo().servicioAsignado()).isEqualTo("Sonido y luces");
        assertThat(enviado.getValue().cuerpo().estado()).isNull(); // el servicio aplica PENDIENTE por defecto
    }

    @Test
    void cambiarEstadoAsignacionReenviaElIdYElEstado() {
        UUID asignacionId = UUID.randomUUID();
        when(proveedores.cambiarEstadoAsignacion(any(), eq(asignacionId), eq("CONFIRMADO")))
                .thenReturn(new ProveedorAsignado(asignacionId, eventoId, UUID.randomUUID(), "Sonido y luces",
                        new BigDecimal("900000"), "CONFIRMADO"));

        tester.document("mutation($id: ID!) { cambiarEstadoAsignacion(id: $id, estado: CONFIRMADO) { id estado } }")
                .variable("id", asignacionId.toString())
                .execute()
                .path("cambiarEstadoAsignacion.estado").entity(String.class).isEqualTo("CONFIRMADO");

        verify(proveedores).cambiarEstadoAsignacion(any(), eq(asignacionId), eq("CONFIRMADO"));
    }

    @Test
    void lasMutationsSinRelacionesHacenUnaSolaLlamada() {
        when(eventos.cancelar(any(), eq(eventoId))).thenReturn(evento("CANCELADO"));

        tester.document("mutation($id: ID!) { cancelarEvento(id: $id) { estado } }")
                .variable("id", eventoId.toString())
                .execute()
                .path("cancelarEvento.estado").entity(String.class).isEqualTo("CANCELADO");

        verify(eventos).cancelar(any(), eq(eventoId));
        verifyNoInteractions(clientes, finanzas, contratos, lugares, proveedores, logistica, notificaciones, auditoria);
    }

    @Test
    void errorInesperadoEsInternalErrorConMensajeNeutro() {
        when(contratos.firmar(any(), eq(eventoId))).thenThrow(new IllegalStateException("detalle interno que no debe salir"));

        tester.document("mutation($id: ID!) { firmarContrato(eventoId: $id) { estado } }")
                .variable("id", eventoId.toString())
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("INTERNAL_ERROR");
                    assertThat(errores.get(0).getMessage()).doesNotContain("detalle interno").doesNotContain("IllegalStateException");
                });
    }
}

package com.maxieventos.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.gateway.config.CargadoresConfig;
import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.dto.Balance;
import com.maxieventos.gateway.dto.Cliente;
import com.maxieventos.gateway.dto.Contrato;
import com.maxieventos.gateway.dto.Cotizacion;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.ItemCompra;
import com.maxieventos.gateway.dto.Logistica;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.Notificacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.Pago;
import com.maxieventos.gateway.dto.Proveedor;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.dto.RegistroAuditoria;
import com.maxieventos.gateway.dto.ServicioAdicional;
import com.maxieventos.gateway.exception.ErrorCampo;
import com.maxieventos.gateway.exception.ErrorRemoto;
import com.maxieventos.gateway.exception.ErroresGraphQl;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import com.maxieventos.gateway.service.remoto.AuditoriaClient;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Queries y campos de Evento con los clientes REST simulados. */
@GraphQlTest({ConsultasController.class, EventoCamposController.class, CamposAnidadosController.class})
@Import({GraphQlConfig.class, CargadoresConfig.class, ErroresGraphQl.class})
class ConsultasGraphQlTest {

    /** Fase 11D: el slice @GraphQlTest no pasa por TokenInterceptor; el rol del token (ADMINISTRADOR) se pone en el contexto. */
    private GraphQlTester tester;

    @Autowired
    void comoAdministrador(org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester base) {
        this.tester = base.mutate().configureExecutionInput((entrada, builder) -> builder.graphQLContext(
                c -> c.put(com.maxieventos.gateway.config.TokenInterceptor.ROL, "ADMINISTRADOR")).build()).build();
    }

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
    private final UUID lugarId = UUID.randomUUID();
    private final UUID proveedorId = UUID.randomUUID();
    private final OffsetDateTime ahora = OffsetDateTime.parse("2026-10-28T15:30:00Z");

    private Evento evento(UUID id, UUID cliente, UUID lugar) {
        return new Evento(id, "Quince años de Sofía Muñoz", LocalDate.of(2026, 12, 5), cliente, "QUINCE_ANOS", 90, 15, 105,
                "NEIVA", lugar, "RESERVADO", ahora);
    }

    private static <T> Pagina<T> pagina(List<T> contenido) {
        return new Pagina<>(contenido, 0, 10, contenido.size(), 1);
    }

    @Test
    void fichaCompletaEnUnaSolaQueryConLosNombresDelEsquema() throws Exception {
        when(eventos.obtener(isNull(), eq(eventoId))).thenReturn(evento(eventoId, clienteId, lugarId));
        when(clientes.obtenerOpcional(any(), eq(clienteId)))
                .thenReturn(new Cliente(clienteId, "María José Peña Ñáñez", "3001234567", "maria@ejemplo.com", "Calle 10"));
        when(finanzas.cotizacion(any(), eq(eventoId))).thenReturn(new Cotizacion(UUID.randomUUID(), eventoId, 105, "NEIVA",
                new BigDecimal("8000000"), BigDecimal.ZERO, new BigDecimal("8700000"), new BigDecimal("2610000"), "COP", ahora,
                "QUINCE_ANOS", 90, 15, 10, 0, new BigDecimal("700000"), BigDecimal.ZERO, new BigDecimal("8700000")));
        when(finanzas.balance(any(), eq(eventoId))).thenReturn(new Balance(eventoId, new BigDecimal("8700000"),
                new BigDecimal("250000"), new BigDecimal("2610000"), new BigDecimal("6340000"), new BigDecimal("8950000"),
                new BigDecimal("2610000"), "COP"));
        when(finanzas.pagos(any(), eq(eventoId), anyInt(), anyInt())).thenReturn(pagina(List.of(new Pago(UUID.randomUUID(),
                "REGISTRADO", "k-1", eventoId, clienteId, new BigDecimal("2610000"), "COP", "TRANSFERENCIA", ahora))));
        when(finanzas.adicionales(any(), eq(eventoId))).thenReturn(List.of(new ServicioAdicional(UUID.randomUUID(), eventoId,
                "decoracion-premium", "Decoración premium", 1, new BigDecimal("250000"), new BigDecimal("250000"), ahora)));
        when(contratos.contrato(any(), eq(eventoId))).thenReturn(new Contrato(UUID.randomUUID(), eventoId,
                "Quince años de Sofía Muñoz", "FIRMADO", ahora, "/api/v1/eventos/x/contrato/pdf", ahora, "María José Peña Ñáñez",
                LocalDate.of(2026, 12, 5), "QUINCE_ANOS", 90, 15, "Salón Los Ángeles", new BigDecimal("8700000"), "COP"));
        when(lugares.obtenerOpcional(any(), eq(lugarId)))
                .thenReturn(new Lugar(lugarId, "Salón Los Ángeles", "Vía a Rivera km 3", "SALON", 200, "ACTIVO"));
        when(proveedores.asignaciones(any(), eq(eventoId), anyInt(), anyInt())).thenReturn(pagina(List.of(
                new ProveedorAsignado(UUID.randomUUID(), eventoId, proveedorId, "Sonido y luces", new BigDecimal("900000"), "PENDIENTE"))));
        when(proveedores.obtenerOpcional(any(), eq(proveedorId)))
                .thenReturn(new Proveedor(proveedorId, "Sonido Huila", "Música", "3109876543", true));
        when(logistica.requerimientos(any(), eq(eventoId))).thenReturn(new Logistica(eventoId, 7, 4, 2));
        when(logistica.compras(any(), eq(eventoId), anyInt(), anyInt()))
                .thenReturn(pagina(List.of(new ItemCompra(UUID.randomUUID(), "Servilletas", "210 unidades", false, "CALCULADO", null))));
        when(notificaciones.deEvento(any(), eq(eventoId), anyInt(), anyInt())).thenReturn(pagina(List.of(new Notificacion(
                UUID.randomUUID(), UUID.randomUUID(), eventoId, "EVENTO_CONFIRMADO", "cliente:" + clienteId,
                "Evento confirmado: Quince años de Sofía Muñoz", "Hola…", "GENERADA", ahora))));
        when(auditoria.deEvento(any(), eq(eventoId), any(), anyInt(), anyInt())).thenReturn(pagina(List.of(new RegistroAuditoria(
                UUID.randomUUID(), "PAGO_REGISTRADO", eventoId, "finanzas-service",
                new ObjectMapper().readTree("{\"tipo\":\"PAGO_REGISTRADO\",\"datos\":{\"monto\":2610000}}"),
                "pagos.registrados", 1, 12L, ahora, ahora))));

        tester.document("""
                query($id: ID!) { evento(id: $id) {
                  id nombreEvento fecha tipoEvento numInvitados zona estado
                  cliente { id nombreCompleto telefono correo }
                  cotizacion { valorBase totalSinAdicionales total depositoInicialRequerido }
                  contrato { estado fechaFirma }
                  pagos { totalElementos contenido { monto fecha metodoPago } }
                  balance { totalCotizado montoAbonado saldoPendiente }
                  lugar { nombre capacidad estado }
                  proveedores { contenido { servicio categoria costoAcordado proveedor { nombre } } }
                  serviciosAdicionales { codigo nombre subtotal }
                  logistica { meseros personalCocina vehiculos compras { contenido { nombreItem } } }
                  notificaciones { contenido { asunto estado } }
                  auditoria { contenido { tipo payload offset } }
                } }""")
                .variable("id", eventoId.toString())
                .execute()
                .path("evento.nombreEvento").entity(String.class).isEqualTo("Quince años de Sofía Muñoz")
                .path("evento.fecha").entity(String.class).isEqualTo("2026-12-05")
                .path("evento.tipoEvento").entity(String.class).isEqualTo("QUINCE_ANOS")
                .path("evento.cliente.nombreCompleto").entity(String.class).isEqualTo("María José Peña Ñáñez")
                .path("evento.cotizacion.totalSinAdicionales").entity(BigDecimal.class).isEqualTo(new BigDecimal("8700000"))
                .path("evento.cotizacion.total").entity(BigDecimal.class).isEqualTo(new BigDecimal("8950000"))
                .path("evento.cotizacion.depositoInicialRequerido").entity(BigDecimal.class).isEqualTo(new BigDecimal("2610000"))
                .path("evento.contrato.estado").entity(String.class).isEqualTo("FIRMADO")
                .path("evento.contrato.fechaFirma").entity(String.class).isEqualTo("2026-10-28T15:30:00.000Z")
                .path("evento.pagos.totalElementos").entity(Long.class).isEqualTo(1L)
                .path("evento.pagos.contenido[0].metodoPago").entity(String.class).isEqualTo("TRANSFERENCIA")
                .path("evento.balance.montoAbonado").entity(BigDecimal.class).isEqualTo(new BigDecimal("2610000"))
                .path("evento.balance.saldoPendiente").entity(BigDecimal.class).isEqualTo(new BigDecimal("6340000"))
                .path("evento.lugar.nombre").entity(String.class).isEqualTo("Salón Los Ángeles")
                .path("evento.proveedores.contenido[0].servicio").entity(String.class).isEqualTo("Sonido y luces")
                .path("evento.proveedores.contenido[0].categoria").entity(String.class).isEqualTo("Música")
                .path("evento.proveedores.contenido[0].proveedor.nombre").entity(String.class).isEqualTo("Sonido Huila")
                .path("evento.serviciosAdicionales[0].nombre").entity(String.class).isEqualTo("Decoración premium")
                .path("evento.logistica.vehiculos").entity(Integer.class).isEqualTo(2)
                .path("evento.logistica.compras.contenido[0].nombreItem").entity(String.class).isEqualTo("Servilletas")
                .path("evento.notificaciones.contenido[0].estado").entity(String.class).isEqualTo("GENERADA")
                .path("evento.auditoria.contenido[0].payload").entity(String.class)
                .isEqualTo("{\"tipo\":\"PAGO_REGISTRADO\",\"datos\":{\"monto\":2610000}}")
                .path("evento.auditoria.contenido[0].offset").entity(Long.class).isEqualTo(12L);

        // proveedor y categoria comparten el DataLoader: una sola llamada a Proveedores por proveedor
        verify(proveedores, times(1)).obtenerOpcional(any(), eq(proveedorId));
    }

    @Test
    void unaQueryQuePideSoloClienteNoLlamaAFinanzasNiAContratos() {
        when(eventos.obtener(any(), eq(eventoId))).thenReturn(evento(eventoId, clienteId, lugarId));
        when(clientes.obtenerOpcional(any(), eq(clienteId)))
                .thenReturn(new Cliente(clienteId, "Lucía Fernández", "3001112233", "lucia@ejemplo.com", null));

        tester.document("query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } } }")
                .variable("id", eventoId.toString())
                .execute()
                .path("evento.cliente.nombreCompleto").entity(String.class).isEqualTo("Lucía Fernández");

        verify(clientes, times(1)).obtenerOpcional(any(), eq(clienteId));
        verifyNoInteractions(finanzas, contratos, lugares, proveedores, logistica, notificaciones, auditoria);
    }

    @Test
    void eventoInexistenteEsUnErrorNotFoundConElMensajeDelServicio() {
        when(eventos.obtener(any(), eq(eventoId))).thenThrow(ServicioRemotoException.deRespuesta("eventos", 404,
                new ErrorRemoto(404, "Not Found", "No existe el evento con id " + eventoId, "/api/v1/eventos/x", null)));

        tester.document("query($id: ID!) { evento(id: $id) { nombreEvento } }")
                .variable("id", eventoId.toString())
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getMessage()).isEqualTo("No existe el evento con id " + eventoId);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                    assertThat(errores.get(0).getPath()).isEqualTo("evento");
                    assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "eventos").containsEntry("status", 404);
                })
                .path("evento").valueIsNull();
    }

    @Test
    void campoSinDatoEsNullSinError() {
        when(eventos.obtener(any(), eq(eventoId))).thenReturn(evento(eventoId, clienteId, null));
        when(contratos.contrato(any(), eq(eventoId))).thenReturn(null);

        tester.document("query($id: ID!) { evento(id: $id) { estado contrato { estado } lugar { nombre } } }")
                .variable("id", eventoId.toString())
                .execute()
                .errors().verify()
                .path("evento.contrato").valueIsNull()
                .path("evento.lugar").valueIsNull();

        verifyNoInteractions(lugares); // sin lugarId no se llama a Lugares
    }

    @Test
    void servicioCaidoDaServiceUnavailableEnSuCampoYElRestoDeLaFichaSeEntrega() {
        when(eventos.obtener(any(), eq(eventoId))).thenReturn(evento(eventoId, clienteId, null));
        when(finanzas.cotizacion(any(), eq(eventoId))).thenThrow(ServicioRemotoException.noDisponible("finanzas", null));
        when(contratos.contrato(any(), eq(eventoId))).thenReturn(null);

        tester.document("query($id: ID!) { evento(id: $id) { nombreEvento cotizacion { valorBase } contrato { estado } } }")
                .variable("id", eventoId.toString())
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("SERVICE_UNAVAILABLE");
                    assertThat(errores.get(0).getPath()).isEqualTo("evento.cotizacion");
                    assertThat(errores.get(0).getMessage())
                            .isEqualTo("El servicio de finanzas no está disponible en este momento. Intenta de nuevo más tarde.");
                    assertThat(errores.get(0).getExtensions()).containsEntry("status", 503);
                })
                .path("evento.nombreEvento").entity(String.class).isEqualTo("Quince años de Sofía Muñoz")
                .path("evento.cotizacion").valueIsNull();
    }

    @Test
    void errorDeValidacionDelServicioConservaLosDetallesPorCampoEnExtensions() {
        when(eventos.listar(any(), any(), any(), any(), any(), anyInt(), anyInt(), any())).thenThrow(
                ServicioRemotoException.deRespuesta("eventos", 400, new ErrorRemoto(400, "Bad Request",
                        "Los datos enviados no son válidos. Revisa los campos indicados.", "/api/v1/eventos",
                        List.of(new ErrorCampo("size", "debe ser menor que o igual a 100")))));

        tester.document("{ eventos(size: 101) { totalElementos } }")
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                    assertThat(errores.get(0).getMessage()).isEqualTo("Los datos enviados no son válidos. Revisa los campos indicados.");
                    assertThat(errores.get(0).getExtensions().get("errores"))
                            .isEqualTo(List.of(Map.of("campo", "size", "mensaje", "debe ser menor que o igual a 100")));
                });
    }

    @Test
    void elListadoHaceUnaLlamadaPorClienteDistintoYNingunaParaEventosSinLugar() {
        UUID otroCliente = UUID.randomUUID();
        when(eventos.listar(any(), eq("RESERVADO"), eq("BODA"), any(), any(), eq(0), eq(10), any())).thenReturn(pagina(List.of(
                evento(UUID.randomUUID(), clienteId, null), evento(UUID.randomUUID(), clienteId, null),
                evento(UUID.randomUUID(), otroCliente, lugarId))));
        when(clientes.obtenerOpcional(any(), any()))
                .thenAnswer(i -> new Cliente(i.getArgument(1), "Cliente " + i.getArgument(1), "3000000000", "c@e.com", null));
        when(lugares.obtenerOpcional(any(), eq(lugarId))).thenReturn(new Lugar(lugarId, "Finca El Paraíso", null, "FINCA", 300, "ACTIVO"));

        tester.document("{ eventos(estado: RESERVADO, tipo: BODA) { totalElementos contenido { id cliente { id } lugar { nombre } } } }")
                .execute()
                .path("eventos.totalElementos").entity(Long.class).isEqualTo(3L)
                .path("eventos.contenido[0].cliente.id").entity(String.class).isEqualTo(clienteId.toString())
                .path("eventos.contenido[1].cliente.id").entity(String.class).isEqualTo(clienteId.toString())
                .path("eventos.contenido[0].lugar").valueIsNull()
                .path("eventos.contenido[2].lugar.nombre").entity(String.class).isEqualTo("Finca El Paraíso");

        verify(clientes, times(1)).obtenerOpcional(any(), eq(clienteId));   // 2 eventos, 1 llamada
        verify(clientes, times(1)).obtenerOpcional(any(), eq(otroCliente));
        verify(lugares, times(1)).obtenerOpcional(any(), any());            // solo el evento con lugar
        verify(finanzas, never()).cotizacion(any(), any());
    }

    @Test
    void argumentoConFormatoInvalidoEsBadRequestSinTraza() {
        tester.document("{ cliente(id: \"no-es-uuid\") { nombreCompleto } }")
                .execute()
                .errors().satisfy(errores -> {
                    assertThat(errores).hasSize(1);
                    assertThat(errores.get(0).getErrorType().toString()).isEqualTo("BAD_REQUEST");
                    assertThat(errores.get(0).getMessage()).doesNotContain("Exception").doesNotContain("at com.");
                });
        verifyNoInteractions(clientes);
    }
}

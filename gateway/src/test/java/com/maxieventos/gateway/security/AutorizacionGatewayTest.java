package com.maxieventos.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.Lugar;
import com.maxieventos.gateway.dto.LugarInput;
import com.maxieventos.gateway.dto.Notificacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.dto.ProveedorAsignado;
import com.maxieventos.gateway.dto.ResultadoRecordatorios;
import com.maxieventos.gateway.service.remoto.AuditoriaClient;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.LugaresClient;
import com.maxieventos.gateway.service.remoto.NotificacionesClient;
import com.maxieventos.gateway.service.remoto.ProveedoresClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fase 11D, primer nivel de la defensa en profundidad (de punta a punta por HTTP, con el TokenInterceptor real): el rol
 * sale del claim {@code rol} del token. Las mutations de administración y {@code Evento.auditoria} exigen ADMINISTRADOR;
 * con el asistente o con un token sin rol (anterior a la 11D) → FORBIDDEN (403, servicio gateway) SIN llamar al servicio.
 * El segundo nivel (los servicios responden 403 si se les llama directamente) lo prueban los servicios.
 */
@SpringBootTest(properties = {TokensDePrueba.PROP_SECRETO, TokensDePrueba.PROP_CORS})
@AutoConfigureMockMvc
class AutorizacionGatewayTest {

    private static final UUID EVENTO = UUID.fromString("af79495b-9065-4b83-b08e-83ca7edabc5a");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @MockitoBean private LugaresClient lugares;
    @MockitoBean private ProveedoresClient proveedores;
    @MockitoBean private EventosClient eventos;
    @MockitoBean private AuditoriaClient auditoria;
    @MockitoBean private NotificacionesClient notificaciones;

    private JsonNode graphql(String token, String query, Map<String, Object> variables) throws Exception {
        String cuerpo = JSON.writeValueAsString(Map.of("query", query, "variables", variables));
        MvcResult inicio = mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", token).content(cuerpo)).andReturn();
        MvcResult fin = inicio.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(inicio)).andReturn() : inicio;
        return JSON.readTree(fin.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static void prohibido(JsonNode respuesta, String campo) {
        JsonNode error = respuesta.path("errors").get(0);
        assertThat(error.path("extensions").path("classification").asText()).isEqualTo("FORBIDDEN");
        assertThat(error.path("extensions").path("status").asInt()).isEqualTo(403);
        assertThat(error.path("extensions").path("servicio").asText()).isEqualTo("gateway");
        assertThat(error.path("message").asText()).isEqualTo("No tienes permiso para realizar esta operación.");
        assertThat(error.path("path").get(error.path("path").size() - 1).asText()).isEqualTo(campo);
    }

    @Test
    void elAsistenteYUnTokenSinRolNoAdministranLugaresNiProveedoresYNoSeLlamaAlServicio() throws Exception {
        Map<String, Object> lugar = Map.of("i", Map.of("nombre", "Salón", "direccion", "Calle 1", "tipo", "SALON", "capacidad", 100));
        Map<String, Object> id = Map.of("id", UUID.randomUUID().toString());
        for (String token : List.of(TokensDePrueba.asistente(), TokensDePrueba.sinRol())) {
            prohibido(graphql(token, "mutation($i: LugarInput!) { crearLugar(input: $i) { id } }", lugar), "crearLugar");
            prohibido(graphql(token, "mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { id } }",
                    Map.of("id", id.get("id"), "i", lugar.get("i"))), "actualizarLugar");
            prohibido(graphql(token, "mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: INACTIVO) { id } }", id),
                    "cambiarEstadoLugar");
            prohibido(graphql(token, "mutation { crearProveedor(input: {nombre: \"F\", categoria: \"SONIDO\", contacto: \"3\"}) { id } }",
                    Map.of()), "crearProveedor");
            prohibido(graphql(token, "mutation($id: ID!) { actualizarProveedor(id: $id, input: {nombre: \"F\", categoria: \"SONIDO\", contacto: \"3\"}) { id } }",
                    id), "actualizarProveedor");
            prohibido(graphql(token, "mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { id } }", id),
                    "cambiarEstadoProveedor");
        }
        verifyNoInteractions(lugares, proveedores);
    }

    @Test
    void elAsistenteVeLaFichaSinAuditoriaYLaAuditoriaSoloDaFORBIDDENEnSuCampo() throws Exception {
        when(eventos.obtener(any(), eq(EVENTO))).thenReturn(new Evento(EVENTO, "Quince años de Lucía", LocalDate.of(2026, 12, 5),
                UUID.randomUUID(), "QUINCE_ANOS", 90, 15, 105, "NEIVA", null, "RESERVADO", OffsetDateTime.now()));
        when(notificaciones.deEvento(any(), eq(EVENTO), anyInt(), anyInt())).thenReturn(new Pagina<Notificacion>(List.of(), 0, 10, 0, 0));

        JsonNode respuesta = graphql(TokensDePrueba.asistente(),
                "query($id: ID!) { evento(id: $id) { nombreEvento notificaciones { totalElementos } auditoria { totalElementos } } }",
                Map.of("id", EVENTO.toString()));

        assertThat(respuesta.path("data").path("evento").path("nombreEvento").asText()).isEqualTo("Quince años de Lucía");
        assertThat(respuesta.path("data").path("evento").path("notificaciones").path("totalElementos").asInt()).isZero();
        prohibido(respuesta, "auditoria");
        assertThat(respuesta.path("errors")).hasSize(1);
        verifyNoInteractions(auditoria);
    }

    @Test
    void elAsistenteSiAsignaYConfirmaProveedoresExistentes() throws Exception {
        ProveedorAsignado asignado = new ProveedorAsignado(UUID.randomUUID(), EVENTO, UUID.randomUUID(), "Sonido",
                new BigDecimal("900000"), "PENDIENTE");
        when(proveedores.asignar(any(), any())).thenReturn(asignado);
        when(proveedores.cambiarEstadoAsignacion(any(), eq(asignado.id()), eq("CONFIRMADO"))).thenReturn(asignado);

        JsonNode asignar = graphql(TokensDePrueba.asistente(), "mutation($i: AsignarProveedorInput!) { asignarProveedor(input: $i) { id } }",
                Map.of("i", Map.of("eventoId", EVENTO.toString(), "proveedorId", asignado.proveedorId().toString(),
                        "servicio", "Sonido", "costoAcordado", 900000)));
        JsonNode confirmar = graphql(TokensDePrueba.asistente(), "mutation($id: ID!) { cambiarEstadoAsignacion(id: $id, estado: CONFIRMADO) { id } }",
                Map.of("id", asignado.id().toString()));

        assertThat(asignar.has("errors")).isFalse();
        assertThat(confirmar.has("errors")).isFalse();
    }

    @Test
    void elAdministradorSiCreaLugares() throws Exception {
        when(lugares.crear(any(), any(LugarInput.class))).thenReturn(new Lugar(UUID.randomUUID(), "Salón", "Calle 1", "SALON", 100, "ACTIVO"));

        JsonNode respuesta = graphql(TokensDePrueba.valido(), "mutation { crearLugar(input: {nombre: \"Salón\", direccion: \"Calle 1\", tipo: \"SALON\", capacidad: 100}) { estado } }",
                Map.of());

        assertThat(respuesta.has("errors")).isFalse();
        assertThat(respuesta.path("data").path("crearLugar").path("estado").asText()).isEqualTo("ACTIVO");
        verify(lugares).crear(any(), any(LugarInput.class)); // el servicio sí se llama (y Lugares lo vuelve a validar)
    }

    /** Fase 11H: la campanita es de los dos roles (sin FORBIDDEN); el gateway pasa el token del usuario tal cual. */
    @Test
    void laAsistenteUsaLaCampanitaConSuPropioToken() throws Exception {
        String token = TokensDePrueba.asistente();
        when(notificaciones.sinLeer(token)).thenReturn(1L);
        when(notificaciones.marcarTodas(token)).thenReturn(1);

        JsonNode contador = graphql(token, "{ notificacionesSinLeer }", Map.of());
        JsonNode marcar = graphql(token, "mutation { marcarTodasNotificacionesLeidas }", Map.of());

        assertThat(contador.has("errors")).isFalse();
        assertThat(contador.path("data").path("notificacionesSinLeer").asInt()).isEqualTo(1);
        assertThat(marcar.path("data").path("marcarTodasNotificacionesLeidas").asInt()).isEqualTo(1);
        verify(notificaciones).sinLeer(token);
        verify(notificaciones).marcarTodas(token);
    }

    private static final String EJECUTAR_RECORDATORIOS = "mutation { ejecutarRecordatorios { hoy umbralesDias revisados enviados "
            + "yaEnviados recordatorios { eventoId nombreEvento fecha diasRestantes umbralDias resultado } } }";

    /** Fase 11E: la ejecución manual de los recordatorios es solo del administrador. */
    @Test
    void elAsistenteYUnTokenSinRolNoEjecutanLosRecordatoriosYNoSeLlamaAlServicio() throws Exception {
        for (String token : List.of(TokensDePrueba.asistente(), TokensDePrueba.sinRol())) {
            JsonNode respuesta = graphql(token, EJECUTAR_RECORDATORIOS, Map.of());
            prohibido(respuesta, "ejecutarRecordatorios");
            assertThat(respuesta.path("data").isNull()).as("campo no nulo: sin datos").isTrue();
        }
        verifyNoInteractions(eventos);
    }

    @Test
    void elAdministradorEjecutaLosRecordatoriosYRecibeElResultadoDelServicio() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 11, 5);
        when(eventos.ejecutarRecordatorios(any())).thenReturn(new ResultadoRecordatorios(OffsetDateTime.parse("2026-11-05T12:00:00Z"),
                hoy, List.of(1, 7), 2, 1, 1, List.of(
                new ResultadoRecordatorios.Recordatorio(EVENTO, "Quince años de Lucía", hoy.plusDays(7), 7, 7, "ENVIADO"),
                new ResultadoRecordatorios.Recordatorio(UUID.randomUUID(), "Boda de Ana", hoy.plusDays(1), 1, 1, "YA_ENVIADO"))));

        String token = TokensDePrueba.valido();
        JsonNode respuesta = graphql(token, EJECUTAR_RECORDATORIOS, Map.of());

        assertThat(respuesta.has("errors")).isFalse();
        JsonNode resultado = respuesta.path("data").path("ejecutarRecordatorios");
        assertThat(resultado.path("hoy").asText()).isEqualTo("2026-11-05");
        assertThat(resultado.path("umbralesDias").toString()).isEqualTo("[1,7]");
        assertThat(resultado.path("enviados").asInt()).isEqualTo(1);
        assertThat(resultado.path("yaEnviados").asInt()).isEqualTo(1);
        assertThat(resultado.path("recordatorios").get(0).path("eventoId").asText()).isEqualTo(EVENTO.toString());
        assertThat(resultado.path("recordatorios").get(0).path("fecha").asText()).isEqualTo("2026-11-12");
        assertThat(resultado.path("recordatorios").get(0).path("resultado").asText()).isEqualTo("ENVIADO");
        assertThat(resultado.path("recordatorios").get(1).path("resultado").asText()).isEqualTo("YA_ENVIADO");
        verify(eventos).ejecutarRecordatorios(token); // el token tal cual: eventos-service vuelve a comprobar el rol
    }
}

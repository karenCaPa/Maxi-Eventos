package com.maxieventos.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.maxieventos.gateway.config.GraphQlConfig;
import com.maxieventos.gateway.config.TokenInterceptor;
import com.maxieventos.gateway.dto.Evento;
import com.maxieventos.gateway.dto.MiNotificacion;
import com.maxieventos.gateway.dto.Pagina;
import com.maxieventos.gateway.exception.ErrorRemoto;
import com.maxieventos.gateway.exception.ErroresGraphQl;
import com.maxieventos.gateway.exception.ServicioRemotoException;
import com.maxieventos.gateway.service.remoto.EventosClient;
import com.maxieventos.gateway.service.remoto.NotificacionesClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Fase 11H: queries y mutations de la campanita; cada una delega en una llamada REST con el token del usuario. */
@GraphQlTest(MisNotificacionesController.class)
@Import({GraphQlConfig.class, ErroresGraphQl.class})
class MisNotificacionesGraphQlTest {

    private static final String TOKEN = "Bearer token-de-la-asistente";
    private GraphQlTester tester;

    /** El slice no pasa por TokenInterceptor: el token se pone en el contexto, como lo haría el interceptor. */
    @Autowired
    void conToken(ExecutionGraphQlServiceTester base) {
        this.tester = base.mutate().configureExecutionInput((entrada, builder) -> builder.graphQLContext(
                c -> c.put(TokenInterceptor.AUTORIZACION, TOKEN).put(TokenInterceptor.ROL, "ASISTENTE")).build()).build();
    }

    @MockitoBean private NotificacionesClient notificaciones;
    @MockitoBean private EventosClient eventos;

    private final UUID boda = UUID.randomUUID();
    private final UUID quince = UUID.randomUUID();

    private MiNotificacion notificacion(UUID eventoId, String asunto, boolean leida) {
        return new MiNotificacion(UUID.randomUUID(), UUID.randomUUID(), eventoId, "EVENTO_PROXIMO", "cliente:c1", asunto,
                "Hola.", "GENERADA", OffsetDateTime.parse("2026-11-06T15:45:00Z"), leida,
                leida ? OffsetDateTime.parse("2026-11-06T16:00:00Z") : null);
    }

    private Evento evento(UUID id, String nombre) {
        return new Evento(id, nombre, LocalDate.of(2026, 11, 7), UUID.randomUUID(), "BODA", 90, 10, 100, "NEIVA", null,
                "RESERVADO", OffsetDateTime.now());
    }

    @Test
    void misNotificacionesConElEventoUnaLlamadaPorEventoDistinto() {
        when(notificaciones.recientes(TOKEN, 0, 10)).thenReturn(new Pagina<>(List.of(
                notificacion(boda, "Falta 1 día para su evento: Boda de Gabriela y Mateo", false),
                notificacion(boda, "Abono registrado", true),
                notificacion(quince, "Evento confirmado: Quince años de Lucía", true)), 0, 10, 3, 1));
        when(eventos.obtenerOpcional(TOKEN, boda)).thenReturn(evento(boda, "Boda de Gabriela y Mateo"));
        when(eventos.obtenerOpcional(TOKEN, quince)).thenReturn(evento(quince, "Quince años de Lucía"));

        tester.document("{ misNotificaciones(page: 0, size: 10) { totalElementos contenido { asunto leida leidaEn fechaCreacion evento { id nombreEvento } } } }")
                .execute()
                .path("misNotificaciones.totalElementos").entity(Long.class).isEqualTo(3L)
                .path("misNotificaciones.contenido[0].leida").entity(Boolean.class).isEqualTo(false)
                .path("misNotificaciones.contenido[0].leidaEn").valueIsNull()
                .path("misNotificaciones.contenido[0].fechaCreacion").entity(String.class).isEqualTo("2026-11-06T15:45:00.000Z")
                .path("misNotificaciones.contenido[0].evento.nombreEvento").entity(String.class).isEqualTo("Boda de Gabriela y Mateo")
                .path("misNotificaciones.contenido[1].leida").entity(Boolean.class).isEqualTo(true)
                .path("misNotificaciones.contenido[2].evento.nombreEvento").entity(String.class).isEqualTo("Quince años de Lucía");

        verify(eventos, times(1)).obtenerOpcional(TOKEN, boda); // dos notificaciones del mismo evento: una sola llamada
        verify(eventos, times(1)).obtenerOpcional(TOKEN, quince);
    }

    @Test
    void sinPedirElEventoNoSeLlamaAEventos() {
        when(notificaciones.recientes(TOKEN, 0, 5)).thenReturn(new Pagina<>(List.of(notificacion(boda, "Abono registrado", false)), 0, 5, 1, 1));

        tester.document("{ misNotificaciones(size: 5) { contenido { asunto } } }").execute()
                .path("misNotificaciones.contenido[0].asunto").entity(String.class).isEqualTo("Abono registrado");
        verifyNoInteractions(eventos);
    }

    @Test
    void notificacionesSinLeerDelUsuarioDelToken() {
        when(notificaciones.sinLeer(TOKEN)).thenReturn(2L);

        tester.document("{ notificacionesSinLeer }").execute()
                .path("notificacionesSinLeer").entity(Integer.class).isEqualTo(2);
    }

    @Test
    void marcarUnaYMarcarTodasDelegan() {
        MiNotificacion leida = notificacion(boda, "Falta 1 día para su evento: Boda de Gabriela y Mateo", true);
        when(notificaciones.marcarLeida(TOKEN, leida.id())).thenReturn(leida);
        when(notificaciones.marcarTodas(TOKEN)).thenReturn(4);

        tester.document("mutation($id: ID!) { marcarNotificacionLeida(id: $id) { id leida leidaEn } }")
                .variable("id", leida.id().toString()).execute()
                .path("marcarNotificacionLeida.leida").entity(Boolean.class).isEqualTo(true)
                .path("marcarNotificacionLeida.leidaEn").entity(String.class).isEqualTo("2026-11-06T16:00:00.000Z");
        tester.document("mutation { marcarTodasNotificacionesLeidas }").execute()
                .path("marcarTodasNotificacionesLeidas").entity(Integer.class).isEqualTo(4);
        verify(notificaciones).marcarLeida(eq(TOKEN), eq(leida.id()));
    }

    @Test
    void marcarUnaInexistenteLlegaComoNotFoundConElMensajeDelServicio() {
        UUID id = UUID.randomUUID();
        when(notificaciones.marcarLeida(any(), eq(id))).thenThrow(ServicioRemotoException.deRespuesta("notificaciones", 404,
                new ErrorRemoto(404, "Not Found", "No existe una notificación con id " + id, "/api/v1/notificaciones/" + id + "/leida", List.of())));

        tester.document("mutation($id: ID!) { marcarNotificacionLeida(id: $id) { id } }").variable("id", id.toString()).execute()
                .errors().satisfy(errores -> {
                    org.assertj.core.api.Assertions.assertThat(errores).hasSize(1);
                    org.assertj.core.api.Assertions.assertThat(errores.get(0).getErrorType().toString()).isEqualTo("NOT_FOUND");
                    org.assertj.core.api.Assertions.assertThat(errores.get(0).getMessage()).isEqualTo("No existe una notificación con id " + id);
                    org.assertj.core.api.Assertions.assertThat(errores.get(0).getExtensions()).containsEntry("servicio", "notificaciones");
                });
    }
}

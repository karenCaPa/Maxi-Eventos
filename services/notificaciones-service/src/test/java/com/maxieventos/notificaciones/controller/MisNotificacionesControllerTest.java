package com.maxieventos.notificaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.notificaciones.dto.MarcadasResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.dto.SinLeerResponse;
import com.maxieventos.notificaciones.exception.GlobalExceptionHandler;
import com.maxieventos.notificaciones.exception.RecursoNoEncontradoException;
import com.maxieventos.notificaciones.service.LecturaNotificacionService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Fase 11H: lo que el controlador de la campanita pasa al servicio (el usuario = principal) y sus errores. */
class MisNotificacionesControllerTest {

    private final LecturaNotificacionService service = mock(LecturaNotificacionService.class);
    private final Principal organizadora = () -> "sub-organizadora";
    private MockMvc mvc;

    @BeforeEach
    void crear() {
        mvc = MockMvcBuilders.standaloneSetup(new MisNotificacionesController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void recientesDelUsuarioActualMasNuevasPrimero() throws Exception {
        when(service.recientes(eq("sub-organizadora"), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 10, 0, 0));

        mvc.perform(get("/api/v1/notificaciones/recientes").principal(organizadora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamanoPagina").value(10));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(service).recientes(eq("sub-organizadora"), pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getSort().getOrderFor("fechaCreacion").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sinLeerYMarcarTodasSonDelUsuarioActual() throws Exception {
        when(service.sinLeer("sub-organizadora")).thenReturn(new SinLeerResponse(3));
        when(service.marcarTodas("sub-organizadora")).thenReturn(new MarcadasResponse(3));

        mvc.perform(get("/api/v1/notificaciones/sin-leer").principal(organizadora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sinLeer").value(3));
        mvc.perform(patch("/api/v1/notificaciones/leidas").principal(organizadora))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marcadas").value(3));
    }

    @Test
    void marcarUnaInexistenteResponde404ConErrorResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.marcarLeida("sub-organizadora", id)).thenThrow(new RecursoNoEncontradoException("No existe una notificación con id " + id));

        mvc.perform(patch("/api/v1/notificaciones/{id}/leida", id).principal(organizadora))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No existe una notificación con id " + id))
                .andExpect(jsonPath("$.path").value("/api/v1/notificaciones/" + id + "/leida"));
    }

    @Test
    void parametrosInvalidosResponden400SinLlamarAlServicio() throws Exception {
        mvc.perform(patch("/api/v1/notificaciones/no-es-uuid/leida").principal(organizadora))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("id"));
        mvc.perform(get("/api/v1/notificaciones/recientes").param("size", "0").principal(organizadora))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notificaciones/recientes").param("size", "101").principal(organizadora))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}

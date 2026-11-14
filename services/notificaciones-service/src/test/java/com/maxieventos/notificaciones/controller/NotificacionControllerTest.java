package com.maxieventos.notificaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.notificaciones.dto.NotificacionResponse;
import com.maxieventos.notificaciones.dto.PaginaResponse;
import com.maxieventos.notificaciones.entity.EstadoNotificacion;
import com.maxieventos.notificaciones.exception.GlobalExceptionHandler;
import com.maxieventos.notificaciones.service.NotificacionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificacionControllerTest {

    private final NotificacionService service = mock(NotificacionService.class);
    private MockMvc mvc;

    @BeforeEach
    void crear() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificacionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void filtraPorEventoYPagina() throws Exception {
        UUID eventoId = UUID.randomUUID();
        var notificacion = new NotificacionResponse(UUID.randomUUID(), UUID.randomUUID(), eventoId, "PAGO_REGISTRADO",
                "cliente:abc", "Abono registrado", "Hola. Registramos su abono…", EstadoNotificacion.GENERADA,
                Instant.parse("2026-10-28T10:00:00Z"));
        when(service.listar(eq(eventoId), any())).thenReturn(new PaginaResponse<>(List.of(notificacion), 1, 2, 3, 2));

        mvc.perform(get("/api/v1/notificaciones").param("eventoId", eventoId.toString()).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].estado").value("GENERADA"))
                .andExpect(jsonPath("$.contenido[0].destinatario").value("cliente:abc"))
                .andExpect(jsonPath("$.pagina").value(1));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listar(eq(eventoId), pagina.capture());
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaCreacion"));
    }

    @Test
    void parametrosInvalidosResponden400() throws Exception {
        mvc.perform(get("/api/v1/notificaciones").param("eventoId", "x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
        mvc.perform(get("/api/v1/notificaciones").param("sort", "mensaje"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("sort"));
        verifyNoInteractions(service);
    }
}

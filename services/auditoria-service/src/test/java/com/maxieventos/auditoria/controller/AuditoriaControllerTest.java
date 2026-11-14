package com.maxieventos.auditoria.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.maxieventos.auditoria.dto.AuditoriaEventoResponse;
import com.maxieventos.auditoria.dto.PaginaResponse;
import com.maxieventos.auditoria.entity.TipoMensaje;
import com.maxieventos.auditoria.exception.GlobalExceptionHandler;
import com.maxieventos.auditoria.service.AuditoriaService;
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

class AuditoriaControllerTest {

    private final AuditoriaService service = mock(AuditoriaService.class);
    private MockMvc mvc;

    @BeforeEach
    void crear() {
        mvc = MockMvcBuilders.standaloneSetup(new AuditoriaController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void filtraPaginaYDevuelveElPayloadComoObjeto() throws Exception {
        UUID eventoId = UUID.randomUUID();
        var registro = new AuditoriaEventoResponse(UUID.randomUUID(), "PAGO_REGISTRADO", eventoId, "finanzas-service",
                "{\"tipo\":\"PAGO_REGISTRADO\",\"datos\":{\"monto\":5}}", "pagos.registrados", 1, 7L,
                Instant.parse("2026-10-28T10:00:00Z"), Instant.parse("2026-10-28T10:00:01Z"));
        when(service.listar(eq(eventoId), eq(TipoMensaje.PAGO_REGISTRADO), any()))
                .thenReturn(new PaginaResponse<>(List.of(registro), 0, 5, 1, 1));

        mvc.perform(get("/api/v1/auditoria").param("eventoId", eventoId.toString()).param("tipo", "PAGO_REGISTRADO")
                        .param("size", "5").param("sort", "fechaOcurrencia,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].payload.datos.monto").value(5))
                .andExpect(jsonPath("$.contenido[0].offset").value(7))
                .andExpect(jsonPath("$.totalElementos").value(1));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listar(eq(eventoId), eq(TipoMensaje.PAGO_REGISTRADO), pagina.capture());
        org.assertj.core.api.Assertions.assertThat(pagina.getValue().getPageSize()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(pagina.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "fechaOcurrencia"));
    }

    @Test
    void ordenPorDefectoEsFechaRecepcionDescendente() throws Exception {
        when(service.listar(any(), any(), any())).thenReturn(new PaginaResponse<>(List.of(), 0, 10, 0, 0));

        mvc.perform(get("/api/v1/auditoria")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listar(eq(null), eq(null), pagina.capture());
        org.assertj.core.api.Assertions.assertThat(pagina.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "fechaRecepcion"));
    }

    @Test
    void parametrosInvalidosResponden400ConElCampo() throws Exception {
        mvc.perform(get("/api/v1/auditoria").param("tipo", "OTRO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("tipo"));
        mvc.perform(get("/api/v1/auditoria").param("eventoId", "no-es-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
        mvc.perform(get("/api/v1/auditoria").param("sort", "payload,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores[0].campo").value("sort"));
        verifyNoInteractions(service);
    }
}

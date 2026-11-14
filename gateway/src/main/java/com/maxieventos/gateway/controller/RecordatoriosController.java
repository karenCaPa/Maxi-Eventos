package com.maxieventos.gateway.controller;

import static com.maxieventos.gateway.config.TokenInterceptor.AUTORIZACION;
import static com.maxieventos.gateway.config.TokenInterceptor.ROL;

import com.maxieventos.gateway.dto.ResultadoRecordatorios;
import com.maxieventos.gateway.security.Permisos;
import com.maxieventos.gateway.service.remoto.EventosClient;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * Fase 11E: ejecución manual de la revisión de recordatorios EVENTO_PROXIMO. Delega en una sola llamada REST
 * ({@code POST /api/v1/recordatorios/ejecutar}); la selección de eventos y la regla de "sin duplicados" están en
 * eventos-service. Solo ADMINISTRADOR: con otro rol (o un token sin rol) responde FORBIDDEN sin llamar al servicio, y
 * eventos-service lo vuelve a comprobar (403) si se le llama directamente.
 */
@Controller
public class RecordatoriosController {
    private final EventosClient eventos;

    public RecordatoriosController(EventosClient eventos) {
        this.eventos = eventos;
    }

    @MutationMapping
    public ResultadoRecordatorios ejecutarRecordatorios(
            @ContextValue(name = AUTORIZACION, required = false) String autorizacion,
            @ContextValue(name = ROL, required = false) String rol) {
        Permisos.exigirAdministrador(rol, "ejecutarRecordatorios");
        return eventos.ejecutarRecordatorios(autorizacion);
    }
}
